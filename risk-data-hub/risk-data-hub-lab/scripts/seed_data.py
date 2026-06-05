#!/usr/bin/env python3
"""
种子数据初始化脚本 — 向中台库写入初始配置数据。

职责：
  1. 自动创建 risk_hub 数据库及表结构（如不存在）
  2. 向 risk_hub 库写入 Leaf 发号器初始值（leaf_alloc 表）
  3. 向 risk_hub 库写入字典映射数据（dict_item 表）
  4. 清空所有业务数据表（可选，通过 --force 参数）

用法：
  python scripts/seed_data.py              # 写入种子数据（幂等）
  python scripts/seed_data.py --force       # 先清空业务表，再写入
  python scripts/seed_data.py --dml-only    # 跳过建库建表，只灌种子数据

配置方式（按优先级）：
  1. 环境变量：DB_HOST, DB_PORT, DB_USER, DB_PASSWORD
  2. 默认值：localhost, 3306, root, 空密码

依赖：
  pip install PyMySQL
"""

import os
import sys

import pymysql

# ---------- 配置 ----------
DB_HOST = os.getenv("DB_HOST", "host.docker.internal")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "123456")
DB_NAME = "risk_hub"

HUB_SCHEMA = "sql/hub/hub-schema.sql"
HUB_SEED = "sql/hub/hub-seed.sql"

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def get_connection(database: str = None):
    """建立 MySQL 连接，可指定数据库名。"""
    return pymysql.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=DB_PASSWORD,
        database=database,
        charset="utf8mb4",
        autocommit=True,
    )


def execute_sql_file(cursor, file_path: str) -> int:
    """
    执行 SQL 文件中的全部语句。

    先移除所有 -- 注释行，再按分号分割逐条执行，避免注释前缀导致含 INSERT 的语句块被整体跳过。
    """
    full_path = os.path.join(PROJECT_ROOT, file_path)
    if not os.path.exists(full_path):
        print(f"  [WARN] SQL 文件不存在: {full_path}")
        return 0

    with open(full_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 移除注释行（以 -- 开头），保留纯 SQL
    clean_lines = [line for line in content.split("\n") if not line.strip().startswith("--")]
    clean_sql = "\n".join(clean_lines)

    count = 0
    for raw_stmt in clean_sql.split(";"):
        stmt = raw_stmt.strip()
        if not stmt:
            continue
        cursor.execute(stmt)
        count += 1

    print(f"  [OK] 执行 {file_path} ({count} 条语句)")
    return count


def ensure_database():
    """创建 risk_hub 数据库（如不存在）。"""
    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            sql = f"CREATE DATABASE IF NOT EXISTS `{DB_NAME}` DEFAULT CHARACTER SET utf8mb4"
            cursor.execute(sql)
            print(f"  [OK] 数据库 '{DB_NAME}' 已就绪")
    finally:
        conn.close()


def ensure_tables():
    """执行 hub-schema.sql 建表（IF NOT EXISTS，幂等）。"""
    conn = get_connection(DB_NAME)
    try:
        with conn.cursor() as cursor:
            execute_sql_file(cursor, HUB_SCHEMA)
    finally:
        conn.close()


def clear_tables(cursor):
    """清空所有业务数据表（按依赖顺序，先子表后父表）。"""
    clear_files = [
        "sql/dml/clear-oms.sql",
        "sql/dml/clear-broker.sql",
        "sql/dml/clear-hub.sql",
    ]
    for file_path in clear_files:
        execute_sql_file(cursor, file_path)


def main():
    """主流程：建库建表 → 写入种子数据。"""
    args = set(sys.argv[1:])
    force = "--force" in args
    dml_only = "--dml-only" in args

    print("=" * 60)
    print("  数据中台 — 种子数据初始化")
    print("=" * 60)
    print(f"  连接: {DB_USER}@{DB_HOST}:{DB_PORT}/{DB_NAME}")

    if force:
        print("  模式: 强制重置（清空 → 写入）")
    elif dml_only:
        print("  模式: 仅灌种子数据（跳过 DDL）")
    else:
        print("  模式: 全自动（建库建表 + 写入）")

    print()

    # 建库建表（除非 --dml-only）
    if not dml_only:
        print("  --- 确保数据库存在 ---")
        ensure_database()

        print("\n  --- 确保表结构存在 ---")
        ensure_tables()
    else:
        print("  --- 跳过 DDL（--dml-only）---\n")

    # 写入种子数据
    print("  --- 写入种子数据 ---")
    conn = get_connection(DB_NAME)
    try:
        with conn.cursor() as cursor:
            if force:
                print("  --- 清空现有数据 ---")
                clear_tables(cursor)
            execute_sql_file(cursor, HUB_SEED)
    except pymysql.Error as e:
        print(f"\n  [ERROR] 数据库错误: {e}", file=sys.stderr)
        sys.exit(1)
    finally:
        conn.close()

    print(f"\n{'=' * 60}")
    print("  种子数据初始化完成")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
