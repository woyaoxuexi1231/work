#!/usr/bin/env python3
"""
上游数据播种脚本 — 向 trade_oms 和 trade_broker 灌入千万级模拟业务数据。

生成规模（默认值：5000 只股票 × 200 个交易日）：
   OMS:   行情 1,000,000 | 交易 5,000,000 | 持仓 1,000,000 | 资金 40,000
   Broker:行情 1,000,000 | 成交 5,000,000 | 持仓 1,000,000 | 资金 40,000
   总计: ~14,000,000 条记录

用法：
  python scripts/seed_upstream.py                          # 默认 5000 只 × 200 天
  python scripts/seed_upstream.py --stocks 10000 --days 60  # 定制规模
  python scripts/seed_upstream.py --force                   # 先清空再灌入
  python scripts/seed_upstream.py --broker-only             # 只灌 Broker

配置方式（按优先级）：
  1. 环境变量：DB_HOST, DB_PORT, DB_USER, DB_PASSWORD
  2. 默认值：localhost, 3306, root, 空密码

依赖：
  pip install PyMySQL
"""

import argparse
import os
import random
import sys
import time
from datetime import datetime, timedelta

import pymysql

# ---------- 配置 ----------
DB_HOST = os.getenv("DB_HOST", "host.docker.internal")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "123456")

# 股票代码前缀池
REAL_SYMBOLS = [sym for sym in [
    "AAPL", "MSFT", "NVDA", "GOOGL", "AMZN", "META", "TSLA", "AMD",
    "INTC", "PYPL", "NFLX", "ADBE", "CRM", "ORCL", "IBM", "JPM",
    "V", "MA", "UNH", "HD", "DIS", "KO", "PEP", "WMT",
    "BA", "CAT", "GE", "XOM", "CVX", "JNJ", "PG", "MRK",
]]

# 投资者 / 客户账户池
OMS_ACCOUNTS = [f"量化策略{i:02d}" for i in range(1, 51)]
BROKER_CLIENTS = [f"{name}账户" for name in
    ["华泰资管", "中信机构", "国君量化", "招商自营", "东方财富",
     "中金公司", "中信建投", "国信证券", "海通机构", "广发资管",
     "申万宏源", "银河证券", "兴业证券", "光大资管", "平安证券",
     "长江证券", "中泰证券", "财通证券", "东吴证券", "方正证券"]]

OMS_STATUSES = ["NEW", "DONE", "CANCEL"]
BROKER_STATUSES = ["A", "S", "X"]

BATCH_SIZE = 2000  # 每批 INSERT 行数
COMMIT_INTERVAL = 20  # 每 N 批提交一次事务

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def get_connection(database: str):
    """建立 MySQL 连接（autocommit=False，手动批量提交）。"""
    return pymysql.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=DB_PASSWORD,
        database=database,
        charset="utf8mb4",
        autocommit=False,
    )


def execute_sql(cursor, sql_file: str):
    """执行 SQL 文件。"""
    path = os.path.join(PROJECT_ROOT, sql_file)
    if not os.path.exists(path):
        print(f"  [WARN] 文件不存在: {path}")
        return
    with open(path, "r", encoding="utf-8") as f:
        for raw in f.read().split(";"):
            stmt = raw.strip()
            if stmt and not stmt.startswith("--"):
                cursor.execute(stmt)


def clear_tables(database: str, clear_file: str):
    """清空指定数据库的业务表。"""
    conn = get_connection(database)
    try:
        with conn.cursor() as cursor:
            execute_sql(cursor, clear_file)
            conn.commit()
        print(f"  [OK] 清空 {database}")
    finally:
        conn.close()


# ==================== 数据生成 ====================

def build_stock_pool(count: int) -> list:
    """生成股票代码池，每只股票附带基础价格和波动率。"""
    pool = []
    for i in range(count):
        if i < len(REAL_SYMBOLS):
            sym = REAL_SYMBOLS[i]
        else:
            region = random.choice(["SH", "SZ", "US"])
            sym = f"{region}{i:07d}"
        exch = random.choice(["XSHG", "XSHE", "XNYS", "XNAS"])
        base_price = random.uniform(5, 500)
        volatility = random.uniform(0.15, 0.40)
        pool.append((sym, exch, base_price, volatility))
    return pool


def generate_trade_days(count: int) -> list:
    """生成交易日列表（跳过周末），返回 datetime 列表。"""
    days = []
    d = datetime.now() - timedelta(days=1)
    while len(days) < count:
        if d.weekday() < 5:
            days.append(d)
        d -= timedelta(days=1)
    return sorted(days)[-count:]


def walk_price(base: float, vol: float) -> tuple:
    """随机游走生成一日 OHLCV。"""
    change = random.gauss(0, vol / 3)
    open_px = round(base * (1 + random.gauss(0, vol / 4)), 4)
    close_px = round(open_px * (1 + change), 4)
    high_px = round(max(open_px, close_px) * (1 + abs(random.gauss(0, vol / 6))), 4)
    low_px = round(min(open_px, close_px) * (1 - abs(random.gauss(0, vol / 6))), 4)
    volume = int(random.uniform(200000, 60000000))
    turnover = round(close_px * volume, 2)
    return open_px, high_px, low_px, close_px, volume, turnover, close_px  # last = next_base


# ==================== OMS 播种 ====================

def seed_oms(cursor, stock_pool, trade_dates, force: bool):
    """向 trade_oms 批量灌入数据。"""
    if force:
        for tbl in ["oms_stock_snapshot", "oms_trade_order", "oms_position_holding", "oms_cash_asset"]:
            cursor.execute(f"TRUNCATE TABLE {tbl}")
        cursor.connection.commit()

    snap_count = 0
    order_count = 0
    pos_count = 0
    cash_count = 0
    n_stocks = len(stock_pool)
    n_days = len(trade_dates)
    total = n_stocks * n_days

    print(f"\n  --- OMS: {n_stocks} 只股票 × {n_days} 天 = {total:,} 行情 / {total * 5:,} 交易 ---")

    # 每只股票维护一个当前价格用于随机游走
    base_prices = {sym: base for sym, exch, base, vol in stock_pool}

    # 按交易日迭代，保证价格连续性
    for day_idx, trade_date in enumerate(trade_dates):
        date_fmt = trade_date.strftime("%Y-%m-%d")
        time_fmt = f"{date_fmt} 09:30:00"

        snap_rows = []
        order_rows = []
        pos_rows = []

        for idx, (sym, exch, base, vol) in enumerate(stock_pool):
            cp = base_prices[sym]
            o, h, l, c, v, t, ncp = walk_price(cp, vol)
            base_prices[sym] = ncp

            # 行情快照
            snap_count += 1
            snap_rows.append(
                f"({snap_count},'{sym}','{exch}','{date_fmt}',{o},{h},{l},{c},{v},{t},0)"
            )

            # 交易记录（每只股票 3-7 笔）
            trades = random.randint(3, 7)
            for j in range(trades):
                qty = random.randint(100, 10000)
                px = round(c * random.uniform(0.98, 1.02), 4)
                investor = OMS_ACCOUNTS[idx % len(OMS_ACCOUNTS)]
                order_count += 1
                order_rows.append(
                    f"({order_count},'OMS-{sym}-{date_fmt}-{j:02d}','{sym}','{investor}',"
                    f"{'B' if j % 2 == 0 else 'S'},{qty},{px},{round(px * qty, 2)},"
                    f"'{random.choice(OMS_STATUSES)}','{time_fmt}',0)"
                )

            # 持仓（每只股票每天 1 条）
            pos_count += 1
            qty = random.randint(1000, 200000)
            cost = round(c * random.uniform(0.95, 1.05), 4)
            mv = round(c * qty, 2)
            pos_rows.append(
                f"({pos_count},'{OMS_ACCOUNTS[idx % len(OMS_ACCOUNTS)]}','{sym}',"
                f"{qty},{qty - random.randint(100, 1000)},{cost},{mv},'{date_fmt}',0)"
            )

        # 批量刷入
        def flush(table, cols, rows, label=""):
            if not rows:
                return
            for i in range(0, len(rows), BATCH_SIZE):
                batch = rows[i:i + BATCH_SIZE]
                cursor.execute(f"INSERT IGNORE INTO {table}({cols}) VALUES " + ",".join(batch))

        flush("oms_stock_snapshot",
              "id,symbol,exchange_code,market_day,open_price,high_price,low_price,close_price,volume_qty,turnover_amount,sync_flag",
              snap_rows)
        flush("oms_trade_order",
              "id,order_no,stock_code,investor_name,side_code,trade_qty,trade_price,order_amount,trade_status,trade_time,sync_flag",
              order_rows)
        flush("oms_position_holding",
              "id,investor_name,stock_code,holding_qty,available_qty,cost_price,market_value,stat_day,sync_flag",
              pos_rows)

        # 资金记录（每天随机 5-15 条）
        for acct in random.sample(OMS_ACCOUNTS, random.randint(5, 15)):
            cash_count += 1
            base_val = random.uniform(1e6, 1e8)
            cursor.execute(
                "INSERT IGNORE INTO oms_cash_asset(id,investor_name,account_no,"
                "cash_balance,frozen_balance,total_asset,stat_day,sync_flag) "
                "VALUES(%s,%s,%s,%s,%s,%s,%s,0)",
                (cash_count, acct, f"OMS-ACCT-{cash_count:04d}",
                 round(base_val, 2), round(base_val * 0.08, 2),
                 round(base_val * 1.75, 2), date_fmt)
            )

        # 定期提交
        if (day_idx + 1) % COMMIT_INTERVAL == 0 or day_idx == n_days - 1:
            cursor.connection.commit()

        # 进度
        pct = (day_idx + 1) / n_days * 100
        print(f"\r    进度 {day_idx + 1}/{n_days} 天 ({pct:.0f}%) | "
              f"行情 {snap_count:,} | 交易 {order_count:,} | 持仓 {pos_count:,} | 资金 {cash_count:,}",
              end="", flush=True)

    print()
    return snap_count, order_count, pos_count, cash_count


# ==================== Broker 播种 ====================

def seed_broker(cursor, stock_pool, trade_dates, force: bool):
    """向 trade_broker 批量灌入数据。"""
    if force:
        for tbl in ["broker_stock_quote", "broker_trade_deal", "broker_position_balance", "broker_fund_account"]:
            cursor.execute(f"TRUNCATE TABLE {tbl}")
        cursor.connection.commit()

    quote_count = 0
    deal_count = 0
    pos_count = 0
    fund_count = 0
    n_stocks = len(stock_pool)
    n_days = len(trade_dates)
    total = n_stocks * n_days

    print(f"\n  --- Broker: {n_stocks} 只股票 × {n_days} 天 = {total:,} 行情 / {total * 5:,} 成交 ---")

    base_prices = {sym: base for sym, exch, base, vol in stock_pool}

    for day_idx, trade_date in enumerate(trade_dates):
        date_fmt = trade_date.strftime("%Y-%m-%d")
        time_fmt = f"{date_fmt} 09:30:00"

        quote_rows = []
        deal_rows = []
        pos_rows = []

        for idx, (sym, exch, base, vol) in enumerate(stock_pool):
            cp = base_prices[sym]
            o, h, l, c, v, t, ncp = walk_price(cp, vol)
            base_prices[sym] = ncp

            # 行情
            quote_count += 1
            quote_rows.append(
                f"({quote_count},'{sym}-{date_fmt}','{sym}','{date_fmt}','{exch}',{o},{h},{l},{c},{v},{t},0)"
            )

            # 成交（3-7 笔）
            deals = random.randint(3, 7)
            for j in range(deals):
                qty = random.randint(100, 10000)
                px = round(c * random.uniform(0.98, 1.02), 4)
                client = BROKER_CLIENTS[idx % len(BROKER_CLIENTS)]
                deal_count += 1
                deal_rows.append(
                    f"({deal_count},'BRK-{sym}-{date_fmt}-{j:02d}','{sym}','{client}',"
                    f"{'1' if j % 2 == 0 else '2'},{qty},{px},{round(px * qty, 2)},"
                    f"'{random.choice(BROKER_STATUSES)}','{time_fmt}',0)"
                )

            # 持仓
            pos_count += 1
            qty = random.randint(1000, 200000)
            cost = round(c * random.uniform(0.95, 1.05), 4)
            mv = round(c * qty, 2)
            pos_rows.append(
                f"({pos_count},'{BROKER_CLIENTS[idx % len(BROKER_CLIENTS)]}','{sym}',{qty},"
                f"{qty - random.randint(100, 1000)},{cost},{mv},'{date_fmt}',0)"
            )

        def flush(table, cols, rows):
            if not rows:
                return
            for i in range(0, len(rows), BATCH_SIZE):
                batch = rows[i:i + BATCH_SIZE]
                cursor.execute(f"INSERT IGNORE INTO {table}({cols}) VALUES " + ",".join(batch))

        flush("broker_stock_quote",
              "id,quote_code,secu_code,trade_day,exchange_name,open_px,high_px,low_px,close_px,vol_num,turnover_amt,sync_flag",
              quote_rows)
        flush("broker_trade_deal",
              "id,deal_code,secu_code,client_full_name,bs_flag,deal_volume,deal_price,turnover_amount,status_mark,deal_at,sync_flag",
              deal_rows)
        flush("broker_position_balance",
              "id,client_full_name,secu_code,current_volume,enable_volume,cost_px,market_amt,biz_date,sync_flag",
              pos_rows)

        for client in random.sample(BROKER_CLIENTS, random.randint(5, 15)):
            fund_count += 1
            base_val = random.uniform(1e6, 1e8)
            cursor.execute(
                "INSERT IGNORE INTO broker_fund_account(id,client_full_name,fund_account_no,"
                "current_balance,frozen_capital,total_asset,biz_date,sync_flag) "
                "VALUES(%s,%s,%s,%s,%s,%s,%s,0)",
                (fund_count, client, f"FUND-{fund_count:04d}",
                 round(base_val, 2), round(base_val * 0.1, 2),
                 round(base_val * 1.65, 2), date_fmt)
            )

        if (day_idx + 1) % COMMIT_INTERVAL == 0 or day_idx == n_days - 1:
            cursor.connection.commit()

        pct = (day_idx + 1) / n_days * 100
        print(f"\r    进度 {day_idx + 1}/{n_days} 天 ({pct:.0f}%) | "
              f"行情 {quote_count:,} | 成交 {deal_count:,} | 持仓 {pos_count:,} | 资金 {fund_count:,}",
              end="", flush=True)

    print()
    return quote_count, deal_count, pos_count, fund_count


# ==================== 主流程 ====================

def main():
    parser = argparse.ArgumentParser(description="上游数据库播种（千万级）")
    parser.add_argument("--stocks", type=int, default=5000, help="股票数量（默认 5000）")
    parser.add_argument("--days", type=int, default=200, help="交易日数量（默认 200）")
    parser.add_argument("--force", action="store_true", help="先清空再灌入")
    parser.add_argument("--oms-only", action="store_true", help="只灌 OMS")
    parser.add_argument("--broker-only", action="store_true", help="只灌 Broker")
    args = parser.parse_args()

    t0 = time.time()
    random.seed(42)

    print("=" * 60)
    print("  上游数据播种（千万级）")
    print("=" * 60)
    print(f"  连接: {DB_USER}@{DB_HOST}:{DB_PORT}")
    print(f"  配置: {args.stocks:,} 只股票 × {args.days} 个交易日")
    print(f"  批次: {BATCH_SIZE:,} 行/INSERT | 提交间隔: {COMMIT_INTERVAL} 批")
    print(f"  预估: OMS {args.stocks * args.days * 7:,} / Broker {args.stocks * args.days * 7:,} 条")

    if args.force:
        print("\n  --- 清空现有数据 ---")
        if not args.broker_only:
            clear_tables("trade_oms", "sql/dml/clear-oms.sql")
        if not args.oms_only:
            clear_tables("trade_broker", "sql/dml/clear-broker.sql")

    print("\n  --- 生成股票池 ---")
    stock_pool = build_stock_pool(args.stocks)
    print(f"  股票池就绪: {len(stock_pool)} 只")

    print("\n  --- 生成交易日历 ---")
    trade_dates = generate_trade_days(args.days)
    print(f"  交易日历就绪: {len(trade_dates)} 天 ({trade_dates[0].strftime('%Y-%m-%d')} ~ {trade_dates[-1].strftime('%Y-%m-%d')})")

    result = {}

    if not args.broker_only:
        print(f"\n{'=' * 60}")
        print(f"  >>> trade_oms")
        oms_conn = get_connection("trade_oms")
        try:
            with oms_conn.cursor() as cursor:
                r = seed_oms(cursor, stock_pool, trade_dates, args.force)
                result["oms"] = r
        finally:
            oms_conn.close()

    if not args.oms_only:
        print(f"\n{'=' * 60}")
        print(f"  >>> trade_broker")
        brk_conn = get_connection("trade_broker")
        try:
            with brk_conn.cursor() as cursor:
                r = seed_broker(cursor, stock_pool, trade_dates, args.force)
                result["broker"] = r
        finally:
            brk_conn.close()

    elapsed = time.time() - t0
    print(f"\n{'=' * 60}")
    print(f"  播种完成  耗时: {elapsed / 60:.1f} 分钟")

    if "oms" in result:
        s, o, p, c = result["oms"]
        print(f"  OMS:   行情 {s:>10,} | 交易 {o:>10,} | 持仓 {p:>10,} | 资金 {c:>10,}")

    if "broker" in result:
        q, d, p, f = result["broker"]
        print(f"  Broker:行情 {q:>10,} | 成交 {d:>10,} | 持仓 {p:>10,} | 资金 {f:>10,}")

    total = sum(sum(v) for v in result.values())
    print(f"  总计: {total:,} 条记录")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
