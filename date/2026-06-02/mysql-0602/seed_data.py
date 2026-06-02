#!/usr/bin/env python3
"""
电商测试数据生成器
==================
一键生成覆盖 MySQL 45 天面试指南全部场景的测试数据。

用法:
    pip install pymysql   # 先装依赖
    python seed_data.py   # 默认连 localhost:3306 root/123456

可选参数:
    python seed_data.py --host 127.0.0.1 --port 3307 --user root --password 123456 --db ecommerce

数据规模:
    users          10,000 行
    categories         50 行  (3级分类树)
    products        1,000 行
    orders         50,000 行
    order_items   150,000 行  (每单平均3个商品)
    payments       45,000 行
    inventory       1,000 行
    order_logs    500,000 行  (分区表)
    reviews        30,000 行
"""

import argparse
import hashlib
import os
import random
import sys
import time
from datetime import datetime, timedelta

# ── 常量池 ──────────────────────────────────────────────

CITIES = [
    "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "南京",
    "西安", "重庆", "苏州", "天津", "长沙", "郑州", "东莞", "青岛",
    "合肥", "佛山", "宁波", "昆明", "沈阳", "大连", "厦门", "福州",
    "无锡", "济南", "南宁", "长春", "哈尔滨", "石家庄", None, None,  # None → IS NULL 练习
]

SURNAMES = [
    "王", "李", "张", "刘", "陈", "杨", "黄", "赵", "周", "吴",
    "徐", "孙", "马", "朱", "胡", "郭", "林", "何", "高", "罗",
    "郑", "梁", "谢", "宋", "唐", "许", "韩", "冯", "邓", "曹",
]

GIVEN_NAMES = [
    "伟", "芳", "娜", "秀英", "敏", "静", "丽", "强", "磊", "洋",
    "勇", "艳", "杰", "娟", "涛", "明", "超", "秀兰", "霞", "平",
    "刚", "桂英", "文", "华", "飞", "玉兰", "斌", "玲", "军", "萍",
]

CATEGORY_TREE = {
    "电子产品": ["手机", "电脑", "平板", "耳机", "智能手表", "相机", "音箱", "游戏机"],
    "服装鞋帽": ["男装", "女装", "童装", "运动鞋", "配饰", "内衣", "箱包"],
    "食品生鲜": ["零食", "饮料", "水果", "蔬菜", "肉类", "海鲜", "乳制品"],
    "家居生活": ["家具", "厨具", "家纺", "灯具", "收纳", "清洁用品"],
    "美妆个护": ["护肤", "彩妆", "洗发护发", "口腔护理", "香水"],
    "图书文娱": ["小说", "教育", "科技", "少儿", "杂志", "文具"],
    "运动户外": ["健身器材", "露营装备", "球类", "游泳", "骑行"],
}

PRODUCT_PREFIXES = {
    "手机": ["华为 Mate", "iPhone", "小米", "OPPO Find", "vivo X", "荣耀"],
    "电脑": ["ThinkPad", "MacBook", "华为 MateBook", "戴尔", "华硕"],
    "平板": ["iPad", "华为 MatePad", "小米平板"],
    "耳机": ["AirPods", "索尼 WH-", "Bose QC", "华为 FreeBuds"],
    "男装": ["商务夹克", "休闲T恤", "牛仔裤", "西裤", "卫衣"],
    "女装": ["连衣裙", "半身裙", "雪纺衫", "针织衫", "风衣"],
    "零食": ["薯片", "坚果", "巧克力", "饼干", "肉干"],
    "饮料": ["可乐", "果汁", "矿泉水", "咖啡", "茶饮料"],
    "护肤": ["面霜", "精华液", "面膜", "防晒霜", "爽肤水"],
    "小说": ["三体", "活着", "百年孤独", "围城", "红楼梦"],
}

PAYMENT_METHODS = ["alipay", "wechat", "credit_card", "debit_card"]
ORDER_STATUSES = ["pending", "paid", "shipped", "completed", "cancelled", "refunded"]
LOG_ACTIONS = {
    "pending→paid":   ("pay",    "pending",   "paid"),
    "paid→shipped":   ("ship",   "paid",      "shipped"),
    "shipped→completed": ("complete", "shipped", "completed"),
    "pending→cancelled": ("cancel", "pending", "cancelled"),
    "paid→refunded":  ("refund", "paid",      "refunded"),
}


# ── 工具函数 ────────────────────────────────────────────

def random_name():
    return random.choice(SURNAMES) + random.choice(GIVEN_NAMES)


def random_phone():
    prefixes = ["138", "139", "150", "151", "186", "187", "188", "176"]
    return random.choice(prefixes) + "".join(random.choices("0123456789", k=8))


def random_email(name):
    domains = ["qq.com", "163.com", "gmail.com", "outlook.com", "aliyun.com"]
    return f"{name}_{random.randint(100, 9999)}@{random.choice(domains)}"


def random_date(start: datetime, end: datetime) -> datetime:
    delta = end - start
    return start + timedelta(seconds=random.randint(0, int(delta.total_seconds())))


# ── 建库建表 SQL ────────────────────────────────────────

DDL_STATEMENTS = [
    # ── users ──
    """
    CREATE TABLE IF NOT EXISTS users (
        id         BIGINT AUTO_INCREMENT PRIMARY KEY,
        name       VARCHAR(50)  NOT NULL,
        email      VARCHAR(100)          COMMENT '部分为NULL, 练IS NULL',
        phone      VARCHAR(20)           COMMENT '部分为NULL',
        city       VARCHAR(50)           COMMENT '部分为NULL, 练GROUP BY',
        age        TINYINT               COMMENT '18-70, 练GROUP BY age bucket',
        gender     ENUM('M','F')         COMMENT '部分为NULL',
        vip_level  TINYINT DEFAULT 0     COMMENT '0-5, 练WHERE vip_level BETWEEN',
        balance    DECIMAL(12,2) DEFAULT 0.00,
        created_at DATETIME NOT NULL,
        status     TINYINT DEFAULT 1     COMMENT '1=正常 0=禁用',
        INDEX idx_city (city),
        INDEX idx_age (age),
        INDEX idx_vip (vip_level),
        INDEX idx_created (created_at),
        INDEX idx_status_vip (status, vip_level)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,

    # ── categories ──
    """
    CREATE TABLE IF NOT EXISTS categories (
        id         INT AUTO_INCREMENT PRIMARY KEY,
        name       VARCHAR(50) NOT NULL,
        parent_id  INT         DEFAULT NULL COMMENT '自引用, 练递归CTE',
        sort_order INT         DEFAULT 0,
        INDEX idx_parent (parent_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,

    # ── products ──
    """
    CREATE TABLE IF NOT EXISTS products (
        id          BIGINT AUTO_INCREMENT PRIMARY KEY,
        name        VARCHAR(200) NOT NULL,
        category_id INT          NOT NULL,
        price       DECIMAL(10,2) NOT NULL,
        cost        DECIMAL(10,2) NOT NULL COMMENT '成本, 练计算毛利率',
        stock       INT DEFAULT 0,
        sales_count INT DEFAULT 0 COMMENT '销量, 练ORDER BY + LIMIT',
        rating      DECIMAL(3,2) DEFAULT 0.00 COMMENT '1-5分',
        description TEXT,
        is_on_sale  TINYINT(1) DEFAULT 1,
        created_at  DATETIME NOT NULL,
        INDEX idx_category (category_id),
        INDEX idx_price (price),
        INDEX idx_sale_price (is_on_sale, price),
        INDEX idx_sales (sales_count),
        INDEX idx_rating (rating)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,

    # ── orders ──
    """
    CREATE TABLE IF NOT EXISTS orders (
        id              BIGINT AUTO_INCREMENT PRIMARY KEY,
        order_no        VARCHAR(32) NOT NULL UNIQUE,
        user_id         BIGINT NOT NULL,
        total_amount    DECIMAL(12,2) NOT NULL,
        discount_amount DECIMAL(10,2) DEFAULT 0.00,
        pay_amount      DECIMAL(12,2) NOT NULL,
        status          ENUM('pending','paid','shipped','completed','cancelled','refunded') DEFAULT 'pending',
        created_at      DATETIME NOT NULL,
        updated_at      DATETIME NOT NULL,
        INDEX idx_user (user_id),
        INDEX idx_status (status),
        INDEX idx_created (created_at),
        INDEX idx_user_created (user_id, created_at),
        INDEX idx_pay_amount (pay_amount)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,

    # ── order_items ──
    """
    CREATE TABLE IF NOT EXISTS order_items (
        id         BIGINT AUTO_INCREMENT PRIMARY KEY,
        order_id   BIGINT NOT NULL,
        product_id BIGINT NOT NULL,
        quantity   INT NOT NULL DEFAULT 1,
        unit_price DECIMAL(10,2) NOT NULL,
        INDEX idx_order (order_id),
        INDEX idx_product (product_id),
        INDEX idx_order_product (order_id, product_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,

    # ── payments ──
    """
    CREATE TABLE IF NOT EXISTS payments (
        id             BIGINT AUTO_INCREMENT PRIMARY KEY,
        order_id       BIGINT NOT NULL UNIQUE,
        amount         DECIMAL(12,2) NOT NULL,
        method         ENUM('alipay','wechat','credit_card','debit_card') NOT NULL,
        transaction_id VARCHAR(64) DEFAULT NULL,
        status         ENUM('success','failed','refunding','refunded') DEFAULT 'success',
        paid_at        DATETIME NOT NULL,
        INDEX idx_method (method),
        INDEX idx_status (status),
        INDEX idx_paid_at (paid_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,

    # ── inventory ──
    """
    CREATE TABLE IF NOT EXISTS inventory (
        product_id     BIGINT PRIMARY KEY,
        stock_quantity INT DEFAULT 0,
        locked_quantity INT DEFAULT 0   COMMENT '锁定库存, 练秒杀扣减',
        version        INT DEFAULT 0    COMMENT '乐观锁版本号',
        updated_at     DATETIME NOT NULL,
        INDEX idx_stock (stock_quantity)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,

    # ── order_logs (分区表) ──
    """
    CREATE TABLE IF NOT EXISTS order_logs (
        id         BIGINT AUTO_INCREMENT,
        order_id   BIGINT NOT NULL,
        action     VARCHAR(50)  NOT NULL,
        old_status VARCHAR(20)  NOT NULL,
        new_status VARCHAR(20)  NOT NULL,
        operator   VARCHAR(50)  DEFAULT 'system',
        remark     TEXT,
        created_at DATETIME NOT NULL,
        PRIMARY KEY (id, created_at),
        INDEX idx_order (order_id),
        INDEX idx_action (action),
        INDEX idx_created (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    PARTITION BY RANGE (TO_DAYS(created_at)) (
        PARTITION p2022 VALUES LESS THAN (TO_DAYS('2023-01-01')),
        PARTITION p2023 VALUES LESS THAN (TO_DAYS('2024-01-01')),
        PARTITION p2024 VALUES LESS THAN (TO_DAYS('2025-01-01')),
        PARTITION p_future VALUES LESS THAN MAXVALUE
    )
    """,

    # ── reviews ──
    """
    CREATE TABLE IF NOT EXISTS reviews (
        id           BIGINT AUTO_INCREMENT PRIMARY KEY,
        order_id     BIGINT NOT NULL,
        product_id   BIGINT NOT NULL,
        user_id      BIGINT NOT NULL,
        rating       TINYINT NOT NULL COMMENT '1-5, 练分布统计',
        content      TEXT,
        is_anonymous TINYINT(1) DEFAULT 0,
        created_at   DATETIME NOT NULL,
        INDEX idx_product (product_id),
        INDEX idx_user (user_id),
        INDEX idx_rating (rating),
        INDEX idx_product_rating (product_id, rating)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
]


# ── 数据生成器 ──────────────────────────────────────────

class DataSeeder:
    def __init__(self, conn):
        self.conn = conn
        self.cursor = conn.cursor()

    def exec(self, sql, params=None):
        self.cursor.execute(sql, params or ())

    def batch_insert(self, table, columns, rows, chunk=2000):
        """批量插入, 每批 chunk 行"""
        placeholders = ", ".join(["%s"] * len(columns))
        cols = ", ".join(columns)
        sql = f"INSERT IGNORE INTO {table} ({cols}) VALUES ({placeholders})"
        total = len(rows)
        for i in range(0, total, chunk):
            batch = rows[i : i + chunk]
            self.cursor.executemany(sql, batch)
            self.conn.commit()
            pct = min(100, (i + len(batch)) * 100 // total)
            print(f"\r  {table}: {i + len(batch)}/{total} ({pct}%)", end="", flush=True)
        print(" ✓")

    # ── 1. categories ──
    def seed_categories(self):
        print("\n📁 生成分类数据...")
        rows = []
        sort = 1
        pid_map = {}
        for parent_name, children in CATEGORY_TREE.items():
            rows.append((parent_name, None, sort))
            pid_map[parent_name] = sort
            sort += 1
            for child_name in children:
                rows.append((child_name, pid_map[parent_name], sort))
                sort += 1
        self.batch_insert("categories", ["name", "parent_id", "sort_order"], rows)

    # ── 2. users ──
    def seed_users(self):
        print("\n👤 生成用户数据 (10,000)...")
        start = datetime(2021, 1, 1)
        end = datetime(2024, 12, 31)
        rows = []
        for _ in range(10_000):
            name = random_name()
            email = random_email(name) if random.random() > 0.05 else None
            phone = random_phone() if random.random() > 0.03 else None
            city = random.choice(CITIES)
            age = random.randint(18, 70)
            gender = random.choice(["M", "F", None])
            vip = random.choices([0, 1, 2, 3, 4, 5], weights=[40, 25, 15, 10, 7, 3])[0]
            balance = round(random.uniform(-50, 50000), 2)
            created = random_date(start, end)
            status = random.choices([1, 0], weights=[95, 5])[0]
            rows.append((name, email, phone, city, age, gender, vip, balance, created, status))
        self.batch_insert(
            "users",
            ["name", "email", "phone", "city", "age", "gender", "vip_level", "balance", "created_at", "status"],
            rows,
        )

    # ── 3. products ──
    def seed_products(self):
        print("\n📦 生成商品数据 (1,000)...")
        # 先查出所有子分类
        self.exec("SELECT id, name FROM categories WHERE parent_id IS NOT NULL")
        sub_cats = self.cursor.fetchall()  # [(id, name), ...]
        start = datetime(2022, 1, 1)
        end = datetime(2024, 6, 30)
        rows = []
        for _ in range(1_000):
            cat_id, cat_name = random.choice(sub_cats)
            prefixes = PRODUCT_PREFIXES.get(cat_name, ["商品"])
            prefix = random.choice(prefixes)
            product_name = f"{prefix} {random.choice(['Pro','Max','Plus','Lite','SE','X','S',''])} {random.randint(1, 99)}代"
            product_name = product_name.replace("  ", " ").strip()
            cost = round(random.uniform(10, 5000), 2)
            price = round(cost * random.uniform(1.2, 4.0), 2)
            stock = random.choices([0, random.randint(1, 5000)], weights=[8, 92])[0]
            sales = random.randint(0, 30_000)
            rating = round(random.uniform(1.0, 5.0), 2) if sales > 0 else 0.00
            on_sale = 1 if random.random() > 0.06 else 0
            created = random_date(start, end)
            desc = f"{product_name} - 高品质{cat_name}, 热销爆款"
            rows.append((product_name, cat_id, price, cost, stock, sales, rating, desc, on_sale, created))
        self.batch_insert(
            "products",
            ["name", "category_id", "price", "cost", "stock", "sales_count", "rating", "description", "is_on_sale", "created_at"],
            rows,
        )

    # ── 4. orders ──
    def seed_orders(self):
        print("\n🛒 生成订单数据 (50,000)...")
        start = datetime(2022, 1, 1)
        end = datetime(2024, 12, 31)
        # 把订单分散在不同时间段, 模拟真实增长
        # 2022: 10000, 2023: 18000, 2024: 22000
        ranges = [
            (datetime(2022, 1, 1), datetime(2022, 12, 31), 10_000),
            (datetime(2023, 1, 1), datetime(2023, 12, 31), 18_000),
            (datetime(2024, 1, 1), datetime(2024, 12, 31), 22_000),
        ]
        rows = []
        for d_start, d_end, count in ranges:
            for i in range(count):
                order_no = hashlib.md5(f"{d_start}{i}{random.random()}".encode()).hexdigest()[:24]
                user_id = random.randint(1, 10_000)
                total = round(random.uniform(20, 20_000), 2)
                discount = round(random.uniform(0, total * 0.3), 2)
                pay = total - discount
                status = random.choices(
                    ORDER_STATUSES,
                    weights=[5, 8, 12, 55, 12, 8],
                )[0]
                created = random_date(d_start, d_end)
                updated = created + timedelta(hours=random.randint(0, 72))
                rows.append((order_no, user_id, total, discount, pay, status, created, updated))
        self.batch_insert(
            "orders",
            ["order_no", "user_id", "total_amount", "discount_amount", "pay_amount", "status", "created_at", "updated_at"],
            rows,
        )

    # ── 5. order_items ──
    def seed_order_items(self):
        print("\n📋 生成订单明细 (150,000)...")
        self.exec("SELECT id FROM orders")
        order_ids = [r[0] for r in self.cursor.fetchall()]
        rows = []
        for oid in order_ids:
            n_items = random.choices([1, 2, 3, 4, 5], weights=[15, 30, 30, 15, 10])[0]
            for _ in range(n_items):
                pid = random.randint(1, 1_000)
                qty = random.randint(1, 5)
                price = round(random.uniform(10, 5000), 2)
                rows.append((oid, pid, qty, price))
        self.batch_insert("order_items", ["order_id", "product_id", "quantity", "unit_price"], rows)

    # ── 6. payments ──
    def seed_payments(self):
        print("\n💳 生成支付数据 (45,000)...")
        # 只给 status != 'pending' 的订单生成支付
        self.exec("SELECT id, pay_amount, status, created_at FROM orders WHERE status != 'pending'")
        paid_orders = self.cursor.fetchall()
        # 随机取 45000
        sample = random.sample(paid_orders, min(45_000, len(paid_orders)))
        rows = []
        for oid, amount, _, created in sample:
            method = random.choice(PAYMENT_METHODS)
            txn_id = f"TXN{hashlib.md5(str(oid).encode()).hexdigest()[:20].upper()}"
            # 大部分成功, 少数失败/退款
            p_status = random.choices(["success", "failed", "refunding", "refunded"], weights=[88, 5, 3, 4])[0]
            paid_at = created + timedelta(minutes=random.randint(1, 120))
            if p_status in ("refunding", "refunded"):
                paid_at = created + timedelta(days=random.randint(1, 30))
            rows.append((oid, amount, method, txn_id, p_status, paid_at))
        self.batch_insert(
            "payments",
            ["order_id", "amount", "method", "transaction_id", "status", "paid_at"],
            rows,
        )

    # ── 7. inventory ──
    def seed_inventory(self):
        print("\n🏬 生成库存数据 (1,000)...")
        now = datetime.now()
        rows = []
        for pid in range(1, 1_001):
            stock = random.randint(0, 10_000)
            locked = random.randint(0, stock // 10)
            version = random.randint(0, 100)
            rows.append((pid, stock, locked, version, now))
        self.batch_insert(
            "inventory",
            ["product_id", "stock_quantity", "locked_quantity", "version", "updated_at"],
            rows,
        )

    # ── 8. order_logs ──
    def seed_order_logs(self):
        print("\n📝 生成订单日志 (500,000)...")
        self.exec("SELECT id, status, created_at FROM orders")
        orders_data = self.cursor.fetchall()
        rows = []
        total = 0
        for oid, final_status, created in orders_data:
            # 模拟状态流转
            flow = []
            if final_status in ("cancelled",):
                flow = ["pending→cancelled"]
            elif final_status in ("refunded",):
                flow = ["pending→paid", "paid→refunded"]
            elif final_status in ("pending",):
                flow = ["pending→paid"] if random.random() < 0.3 else []
            elif final_status == "paid":
                flow = ["pending→paid"]
            elif final_status == "shipped":
                flow = ["pending→paid", "paid→shipped"]
            elif final_status == "completed":
                flow = ["pending→paid", "paid→shipped", "shipped→completed"]

            ts = created
            for transition in flow:
                action, old_s, new_s = LOG_ACTIONS[transition]
                ts = ts + timedelta(seconds=random.randint(10, 3600))
                operator = random.choices(["system", "admin", "user"], weights=[70, 20, 10])[0]
                remark = f"订单状态从 {old_s} 变更为 {new_s}"
                rows.append((oid, action, old_s, new_s, operator, remark, ts))
                total += 1
        self.batch_insert(
            "order_logs",
            ["order_id", "action", "old_status", "new_status", "operator", "remark", "created_at"],
            rows,
        )

    # ── 9. reviews ──
    def seed_reviews(self):
        print("\n⭐ 生成评价数据 (30,000)...")
        # 给完成的订单生成评价
        self.exec("SELECT id, user_id, created_at FROM orders WHERE status = 'completed'")
        completed = self.cursor.fetchall()
        sample = random.sample(completed, min(30_000, len(completed)))
        rows = []
        for oid, uid, created in sample:
            # 查这单买了哪些商品
            self.exec("SELECT product_id FROM order_items WHERE order_id = %s", (oid,))
            pids = [r[0] for r in self.cursor.fetchall()]
            for pid in pids[: random.randint(1, len(pids))]:
                rating = random.choices([1, 2, 3, 4, 5], weights=[3, 5, 15, 35, 42])[0]
                contents = [
                    "质量很好, 推荐购买!",
                    "一般般, 对得起价格",
                    "物流快, 包装好",
                    "不太满意, 有点失望",
                    "性价比很高, 会回购",
                    "跟描述一致, 好评",
                    "颜色有点色差, 其他还行",
                    "用了一段时间了, 还不错",
                    "客服态度好, 问题解决了",
                    None,  # 有人不写内容
                ]
                content = random.choice(contents)
                is_anon = 1 if random.random() < 0.1 else 0
                review_at = created + timedelta(days=random.randint(1, 30))
                rows.append((oid, pid, uid, rating, content, is_anon, review_at))
        self.batch_insert(
            "reviews",
            ["order_id", "product_id", "user_id", "rating", "content", "is_anonymous", "created_at"],
            rows,
        )


# ── 主流程 ──────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(description="MySQL 测试数据生成器")
    p.add_argument("--host", default="192.168.3.100")
    p.add_argument("--port", type=int, default=3306)
    p.add_argument("--user", default="root")
    p.add_argument("--password", default="123456")
    p.add_argument("--db", default="ecommerce")
    p.add_argument("--drop", action="store_true", help="先 DROP DATABASE 再重建")
    return p.parse_args()


def main():
    args = parse_args()

    try:
        import pymysql
    except ImportError:
        print("❌ 需要 pymysql: pip install pymysql")
        sys.exit(1)

    # 连接 (先不指定 database)
    conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        charset="utf8mb4",
        autocommit=True,
    )

    print("=" * 60)
    print("  MySQL 测试数据生成器")
    print(f"  目标: {args.host}:{args.port}/{args.db}")
    print("=" * 60)

    with conn.cursor() as cur:
        if args.drop:
            cur.execute(f"DROP DATABASE IF EXISTS `{args.db}`")
            print(f"  🗑  DROP DATABASE {args.db}")

        cur.execute(f"CREATE DATABASE IF NOT EXISTS `{args.db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        cur.execute(f"USE `{args.db}`")

        # 建表
        print("\n🔧 建表...")
        for ddl in DDL_STATEMENTS:
            try:
                cur.execute(ddl)
            except pymysql.err.OperationalError as e:
                # 分区表可能报不支持分区的 warning, 忽略
                if "partition" in str(e).lower():
                    print(f"  ⚠ 分区表创建失败 (可能不支持分区): {e}")
                    # 创建不带分区的版本
                    cur.execute(ddl.replace(
                        "PARTITION BY RANGE (TO_DAYS(created_at)) (\n"
                        "        PARTITION p2022 VALUES LESS THAN (TO_DAYS('2023-01-01')),\n"
                        "        PARTITION p2023 VALUES LESS THAN (TO_DAYS('2024-01-01')),\n"
                        "        PARTITION p2024 VALUES LESS THAN (TO_DAYS('2025-01-01')),\n"
                        "        PARTITION p_future VALUES LESS THAN MAXVALUE\n"
                        "    )", ""
                    ))
                else:
                    raise
        print("  ✓ 全部表建好")

    seeder = DataSeeder(conn)
    t0 = time.time()

    seeder.seed_categories()
    seeder.seed_users()
    seeder.seed_products()
    seeder.seed_orders()
    seeder.seed_order_items()
    seeder.seed_payments()
    seeder.seed_inventory()
    seeder.seed_order_logs()
    seeder.seed_reviews()

    elapsed = time.time() - t0
    print(f"\n{'=' * 60}")
    print(f"  ✅ 数据生成完成! 耗时 {elapsed:.1f}s")
    print(f"  数据库: {args.db}")
    print(f"{'=' * 60}")

    # 打印统计
    with conn.cursor() as cur:
        tables = ["users", "categories", "products", "orders", "order_items",
                   "payments", "inventory", "order_logs", "reviews"]
        print("\n📊 数据统计:")
        for tbl in tables:
            try:
                cur.execute(f"SELECT COUNT(*) FROM `{tbl}`")
                cnt = cur.fetchone()[0]
                print(f"  {tbl:20s} {cnt:>10,} 行")
            except Exception:
                print(f"  {tbl:20s}    (跳过)")

    conn.close()


if __name__ == "__main__":
    main()
