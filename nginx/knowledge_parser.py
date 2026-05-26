#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
MD 文件解析守护脚本
每 5 分钟解析一次指定 MD 文件中的 ## 二级标题及内容，
存入 MySQL knowledge 表（标题+内容完全一致判重）。
"""

import os
import re
import sys
import time
import signal
import pymysql

# 数据库配置（与日志大屏服务一致）
DB_CONFIG = {
    'host': '192.168.3.100',
    'user': 'root',
    'password': '123456',
    'database': 'test',
    'port': 3306,
    'charset': 'utf8mb4'
}

# 默认解析的文件列表（相对于此脚本所在目录）
DEFAULT_MD_FILES = [
    'D:\\project\\work\\src\\main\\java\\work\\N1javabasic\\deepseek\\plan.md'
]


def get_db_connection():
    """获取数据库连接"""
    return pymysql.connect(**DB_CONFIG)


def ensure_table():
    """确保表存在"""
    conn = get_db_connection()
    cursor = conn.cursor()
    sql = """
    CREATE TABLE IF NOT EXISTS `knowledge` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
        `category` VARCHAR(255) NOT NULL COMMENT '分类（MD文件名）',
        `title` TEXT NOT NULL COMMENT '题目（## 二级标题）',
        `content` LONGTEXT NOT NULL COMMENT '答案（标题下所有内容）',
        `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
        `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
        `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
        `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
        PRIMARY KEY (`id`),
        KEY `idx_category` (`category`),
        KEY `idx_create_time` (`create_time`),
        KEY `idx_update_time` (`update_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表（MD解析）';
    """
    cursor.execute(sql)
    conn.commit()
    cursor.close()
    conn.close()
    print(f"[OK] 表 knowledge 已就绪")


def parse_md_file(filepath):
    """
    解析单个 MD 文件，返回 (category, questions) 元组。
    questions 为 [(title, content), ...]
    """
    category = os.path.splitext(os.path.basename(filepath))[0]

    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    questions = []
    current_title = None
    current_content_lines = []
    in_code_block = False
    collecting = False  # 是否在收集内容（已遇到 ## 标题）

    for i, line in enumerate(lines):
        stripped = line.rstrip('\n').rstrip('\r')

        # 检测代码块开始/结束
        if stripped.strip().startswith('```'):
            in_code_block = not in_code_block
            # 如果正在收集，保留代码块标记行
            if collecting:
                current_content_lines.append(line)
            continue

        # 在代码块内，不处理标题，但保留内容
        if in_code_block:
            if collecting:
                current_content_lines.append(line)
            continue

        # 检查是否为标题（仅在代码块外）
        heading_match = re.match(r'^(#{1,3})\s+(.+)$', stripped.strip())
        if heading_match:
            level = len(heading_match.group(1))
            heading_text = heading_match.group(2).strip()

            if level == 2:
                # 遇到 ## 二级标题 → 保存上一个，开始新的
                if current_title is not None:
                    questions.append((current_title, ''.join(current_content_lines).strip()))
                current_title = heading_text
                current_content_lines = []
                collecting = True
            elif level == 1 and collecting:
                # 遇到 # 一级标题 → 保存当前，重置
                if current_title is not None:
                    questions.append((current_title, ''.join(current_content_lines).strip()))
                current_title = None
                current_content_lines = []
                collecting = False
            elif level == 3 and collecting:
                # ### 子标题 → 作为内容保留
                current_content_lines.append(line)
            continue

        # 非标题行
        if collecting:
            current_content_lines.append(line)

    # 文件结尾，保存最后一个
    if current_title is not None and collecting:
        questions.append((current_title, ''.join(current_content_lines).strip()))

    return category, questions


def save_to_mysql(category, questions):
    """将解析结果存入 MySQL，判断标题+内容完全一致则跳过"""
    conn = get_db_connection()
    cursor = conn.cursor()

    insert_sql = """
        INSERT INTO knowledge (category, title, content)
        VALUES (%s, %s, %s)
    """
    check_sql = """
        SELECT COUNT(*) FROM knowledge
        WHERE category = %s AND title = %s AND content = %s AND is_deleted = 0
    """

    added = 0
    skipped = 0

    for title, content in questions:
        if not title or not content:
            continue

        cursor.execute(check_sql, (category, title, content))
        count = cursor.fetchone()[0]

        if count == 0:
            cursor.execute(insert_sql, (category, title, content))
            added += 1
            print(f"  [+] 新增: [{category}] {title[:40]}...")
        else:
            skipped += 1

    conn.commit()
    cursor.close()
    conn.close()

    print(f"[OK] {category}: 新增 {added} 条, 跳过 {skipped} 条")
    return added


running = True


def signal_handler(signum, frame):
    """优雅退出"""
    global running
    print(f"\n[INFO] 收到信号 {signum}，正在退出...")
    running = False


def main():
    md_files = []
    for f in DEFAULT_MD_FILES:
        if os.path.exists(f):
            md_files.append(f)
        else:
            print(f"[WARN] 文件不存在，跳过: {f}")

    if not md_files:
        print("[ERROR] 未找到默认 MD 文件，退出")
        sys.exit(1)

    # 注册信号处理
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    # 确保表存在
    ensure_table()

    print(f"[INFO] 知识库解析守护启动，每 5 分钟扫描一次")
    print(f"[INFO] 监控文件: {', '.join(md_files)}")

    while running:
        print(f"\n{'='*60}")
        print(f"[INFO] 开始扫描: {time.strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"{'='*60}")

        total_added = 0
        for md_file in md_files:
            if not running:
                break

            print(f"\n解析文件: {md_file}")

            category, questions = parse_md_file(md_file)
            print(f"  解析到 {len(questions)} 个题目")

            added = save_to_mysql(category, questions)
            total_added += added

        print(f"\n{'='*60}")
        print(f"[INFO] 本轮完成: {time.strftime('%Y-%m-%d %H:%M:%S')}, 共新增 {total_added} 条")
        print(f"{'='*60}")

        # 等待 5 分钟，期间每秒检查一次退出标志
        if running:
            print(f"[INFO] 等待 5 分钟后下一次扫描...\n")
            for _ in range(300):
                if not running:
                    break
                time.sleep(1)

    print("[INFO] 已退出")


if __name__ == '__main__':
    main()
