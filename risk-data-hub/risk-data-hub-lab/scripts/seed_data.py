#!/usr/bin/env python3
"""
种子数据初始化脚本 — 向中台库写入初始配置数据。

职责：
  1. 向 risk_hub 库写入 Leaf 发号器初始值（leaf_alloc 表）
  2. 向 risk_hub 库写入字典映射数据（dict_item 表）
  3. 清空所有业务数据表（可选，通过 --force 参数）

用法：
  python scripts/seed_data.py              # 仅写入种子数据（幂等）
  python scripts/seed_data.py --force       # 先清空业务表，再写入种子数据

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

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def get_connection():
    """建立 MySQL 连接到 risk_hub 数据库。"""
    return pymysql.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=DB_PASSWORD,
        database=DB_NAME,
        charset="utf8mb4",
        autocommit=True,
    )


def execute_sql_file(cursor, file_path: str) -> int:
    """执行 SQL 文件中的全部语句（按分号分割），返回执行条数。"""
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


def clear_tables(cursor):
    """清空所有业务数据表（按依赖顺序，先子表后父表）。"""
    clear_files = [
        "sql/dml/clear-oms.sql",
        "sql/dml/clear-broker.sql",
        "sql/dml/clear-hub.sql",
    ]
    print("\n  --- 清空业务数据表 ---")
    for file_path in clear_files:
        execute_sql_file(cursor, file_path)


def main():
    """主流程：写入种子数据。"""
    force = "--force" in sys.argv

    print("=" * 60)
    print("  数据中台 — 种子数据初始化")
    print("=" * 60)
    print(f"  连接: {DB_USER}@{DB_HOST}:{DB_PORT}/{DB_NAME}")
    if force:
        print("  模式: 强制重置（先清空再写入）")
    else:
        print("  模式: 增量写入（幂等，INSERT IGNORE）")
    print()

    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            # 清空（可选）
            if force:
                clear_tables(cursor)

            # 写入种子数据
            print("\n  --- 写入种子数据 ---")
            seed_path = "sql/dml/hub-seed.sql"
            execute_sql_file(cursor, seed_path)

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
