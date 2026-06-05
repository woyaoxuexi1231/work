#!/usr/bin/env python3
"""
数据库初始化脚本 — 创建数据库并执行 DDL 建表。

职责：
  1. 创建 risk_hub / trade_oms / trade_broker 三个数据库（如不存在）
  2. 对每个数据库执行对应的 schema DDL 文件

用法：
  python scripts/init_databases.py

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

# Schema 定义：数据库名 → DDL 文件路径（相对于项目根目录）
SCHEMAS = {
    "risk_hub": "sql/ddl/hub-schema.sql",
    "trade_oms": "sql/ddl/oms-schema.sql",
    "trade_broker": "sql/ddl/broker-schema.sql",
}

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


def create_database_if_not_exists(cursor, db_name: str):
    """创建数据库（如不存在），使用 utf8mb4 字符集。"""
    sql = f"CREATE DATABASE IF NOT EXISTS `{db_name}` DEFAULT CHARACTER SET utf8mb4"
    cursor.execute(sql)
    print(f"  [OK] 数据库 '{db_name}' 已就绪")


def execute_sql_file(cursor, file_path: str):
    """执行 SQL 文件中的全部语句（按分号分割）。"""
    full_path = os.path.join(PROJECT_ROOT, file_path)
    if not os.path.exists(full_path):
        print(f"  [WARN] SQL 文件不存在: {full_path}")
        return 0

    with open(full_path, "r", encoding="utf-8") as f:
        content = f.read()

    count = 0
    for statement in content.split(";"):
        stmt = statement.strip()
        if not stmt or stmt.startswith("--"):
            continue
        cursor.execute(stmt)
        count += 1

    print(f"  [OK] 执行 {file_path} ({count} 条语句)")
    return count


def main():
    """主流程：创建所有数据库并执行 schema DDL。"""
    print("=" * 60)
    print("  数据中台 — 数据库初始化")
    print("=" * 60)
    print(f"  连接: {DB_USER}@{DB_HOST}:{DB_PORT}\n")

    # Step 1: 创建数据库（连接到 MySQL 实例，不指定数据库）
    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            for db_name in SCHEMAS:
                create_database_if_not_exists(cursor, db_name)
    finally:
        conn.close()

    # Step 2: 对每个数据库执行 schema DDL
    total_statements = 0
    for db_name, sql_file in SCHEMAS.items():
        print(f"\n  --- {db_name} ({sql_file}) ---")
        db_conn = get_connection(db_name)
        try:
            with db_conn.cursor() as cursor:
                total_statements += execute_sql_file(cursor, sql_file)
        finally:
            db_conn.close()

    print(f"\n{'=' * 60}")
    print(f"  初始化完成，共执行 {total_statements} 条 DDL 语句")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
