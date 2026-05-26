#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Nginx JSON日志采集器 - 直接写入MySQL版本
读取Nginx的JSON格式日志文件，直接写入MySQL数据库
支持从自定义请求头获取真实IP（解决内网穿透无法获取真实IP的问题）
"""

import json
import pymysql
import time
import os
from datetime import datetime

# ==================== 配置区 ====================
# Nginx JSON日志文件路径
NGINX_LOG_FILE = "/var/log/nginx/access_json.log"

# MySQL数据库配置
MYSQL_CONFIG = {
    'host': '192.168.3.100',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'test',
    'charset': 'utf8mb4'
}

# 批量插入大小
BATCH_SIZE = 100

# 检查间隔（秒）
CHECK_INTERVAL = 5

# 记录读取位置的文件
POSITION_FILE = "nginx_log_position.txt"

# 自定义真实IP请求头（优先级从高到低）
# 前端可以将获取到的真实IP塞入这些头中
# 使用下划线命名，因为Nginx日志中会把连字符转成下划线
# X-Custom-Real-IP 放在最前面，用于绕过内网穿透拦截
REAL_IP_HEADERS = [
    'http_x_custom_real_ip',  # 自定义头: X-Custom-Real-IP（绕过内网穿透拦截，优先级最高）
    'http_x_real_ip',         # 自定义头: X-Real-IP
    'http_x_client_ip',       # 自定义头: X-Client-IP  
    'http_x_forwarded_for',   # 标准转发头: X-Forwarded-For
    'http_x_remote_addr',     # 自定义头: X-Remote-Addr
    'http_x_public_ip'        # 自定义头: X-Public-IP
]
# ===============================================


def get_last_position():
    """获取上次读取的位置"""
    if os.path.exists(POSITION_FILE):
        with open(POSITION_FILE, 'r', encoding='utf-8') as f:
            try:
                return int(f.read().strip())
            except:
                return 0
    return 0


def save_position(position):
    """保存当前读取位置"""
    with open(POSITION_FILE, 'w', encoding='utf-8') as f:
        f.write(str(position))


def parse_nginx_time(time_str):
    """
    解析Nginx时间格式并转换为本地时间
    输入: 09/May/2026:08:14:22 +0000 (UTC时间)
    输出: 2026-05-09 16:14:22 (本地时间，UTC+8)
    """
    try:
        dt = datetime.strptime(time_str, '%d/%b/%Y:%H:%M:%S %z')
        # 转换为本地时间
        local_dt = dt.astimezone()
        return local_dt.strftime('%Y-%m-%d %H:%M:%S')
    except Exception as e:
        print(f"⚠️ 时间解析失败: {e}, 原始时间: {time_str}")
        return datetime.now().strftime('%Y-%m-%d %H:%M:%S')


def get_real_ip(log_data):
    """
    从日志中获取真实IP
    优先从自定义请求头获取，然后回退到remote_addr
    
    工作原理:
    1. 前端通过 JavaScript 获取客户端真实IP（如通过第三方API）
    2. 前端将IP塞入自定义请求头（如 X-Real-IP）
    3. Nginx 将请求头记录到日志中
    4. 本函数按优先级从请求头中提取真实IP
    5. 如果所有自定义头都为空，回退到 remote_addr
    6. 如果获取到的是内网IP，返回空字符串（表示未获取到真实公网IP）
    """
    # 先尝试从自定义请求头获取（按优先级）
    for header in REAL_IP_HEADERS:
        ip = log_data.get(header, '').strip()
        if ip and ip != '-':
            # 如果是多个IP（逗号分隔，如 X-Forwarded-For），取第一个
            if ',' in ip:
                ip = ip.split(',')[0].strip()
            # 验证IP格式（简单验证）
            if is_valid_ip(ip):
                # 如果是内网IP，跳过（继续尝试下一个头）
                if is_private_ip(ip):
                    print(f"⚠️ 跳过内网IP [{header}]: {ip}")
                    continue
                # 打印获取到的真实IP（调试用）
                print(f"📡 获取真实IP [{header}]: {ip}")
                return ip
    
    # 回退到 remote_addr
    remote_addr = log_data.get('remote_addr', '')
    if is_valid_ip(remote_addr):
        # 如果是内网IP，不存储
        if is_private_ip(remote_addr):
            print(f"⚠️ remote_addr 是内网IP，不存储: {remote_addr}")
            return ''
        print(f"📡 使用 remote_addr 作为真实IP: {remote_addr}")
        return remote_addr
    
    print(f"❌ 无法获取真实IP，remote_addr: {remote_addr}")
    return ''


def is_valid_ip(ip):
    """简单验证IP地址格式"""
    if not ip or ip == '-':
        return False
    parts = ip.split('.')
    if len(parts) != 4:
        return False
    for part in parts:
        if not part.isdigit():
            return False
        if int(part) < 0 or int(part) > 255:
            return False
    return True


def is_private_ip(ip):
    """判断是否为内网IP"""
    if not is_valid_ip(ip):
        return False
    
    parts = list(map(int, ip.split('.')))
    
    # 内网IP范围：
    # 10.0.0.0 - 10.255.255.255
    if parts[0] == 10:
        return True
    # 172.16.0.0 - 172.31.255.255
    if parts[0] == 172 and 16 <= parts[1] <= 31:
        return True
    # 192.168.0.0 - 192.168.255.255
    if parts[0] == 192 and parts[1] == 168:
        return True
    # 127.0.0.0 - 127.255.255.255 (回环地址)
    if parts[0] == 127:
        return True
    # 169.254.0.0 - 169.254.255.255 (链路本地地址)
    if parts[0] == 169 and parts[1] == 254:
        return True
    
    return False


def create_mysql_connection():
    """创建MySQL连接"""
    try:
        connection = pymysql.connect(**MYSQL_CONFIG)
        return connection
    except Exception as e:
        print(f"🚫 连接MySQL失败: {e}")
        return None


def insert_logs_to_mysql(logs):
    """
    批量插入日志到MySQL
    """
    if not logs:
        return
    
    connection = create_mysql_connection()
    if not connection:
        return
    
    try:
        with connection.cursor() as cursor:
            # 构建SQL插入语句（精简版：只保留real_ip字段存储自定义获取的IP）
            sql = """
                INSERT INTO nginx_access_log (
                    remote_addr, remote_user, time_local, request_method, request_uri,
                    request_protocol, status, body_bytes_sent, http_referer, http_user_agent,
                    http_x_forwarded_for,
                    upstream_addr, upstream_response_time, request_time, host,
                    real_ip
                ) VALUES (
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s,
                    %s, %s, %s, %s, %s
                )
            """
            
            # 准备数据
            values = []
            for log in logs:
                # 解析时间
                time_local = parse_nginx_time(log.get('time_local', ''))
                
                # 获取真实IP（从自定义请求头或remote_addr）
                real_ip = get_real_ip(log)
                
                # 处理数值字段（可能为 "-" 或空字符串）
                def safe_float(val):
                    if not val or val == '-' or val == '':
                        return None
                    try:
                        return float(val)
                    except:
                        return None
                
                value = (
                    log.get('remote_addr', ''),
                    log.get('remote_user', ''),
                    time_local,
                    log.get('request_method', ''),
                    log.get('request_uri', ''),
                    log.get('request_protocol', ''),
                    int(log.get('status', 0)) if log.get('status') else None,
                    int(log.get('body_bytes_sent', 0)) if log.get('body_bytes_sent') else None,
                    log.get('http_referer', ''),
                    log.get('http_user_agent', ''),
                    log.get('http_x_forwarded_for', ''),  # HTTP协议标准字段，保留
                    log.get('upstream_addr', ''),
                    safe_float(log.get('upstream_response_time')),
                    safe_float(log.get('request_time')),
                    log.get('host', ''),
                    real_ip  # 从自定义头提取的真实IP（唯一存储点）
                )
                values.append(value)
            
            # 批量执行
            cursor.executemany(sql, values)
            connection.commit()
            
            print(f"✅ 成功插入 {len(logs)} 条日志")
    
    except Exception as e:
        print(f"🚫 插入日志失败: {e}")
        connection.rollback()
    
    finally:
        connection.close()


def read_new_logs():
    """读取新的日志条目"""
    current_position = get_last_position()
    
    # 检查日志文件是否存在
    if not os.path.exists(NGINX_LOG_FILE):
        print(f"❌ 日志文件不存在: {NGINX_LOG_FILE}")
        return
    
    # 获取文件大小
    file_size = os.path.getsize(NGINX_LOG_FILE)
    
    # 如果文件被截断（比如日志轮转），从头开始读取
    if file_size < current_position:
        current_position = 0
        print("⚠️ 日志文件被截断，从头开始读取")
    
    if file_size == current_position:
        # 没有新日志
        return
    
    logs = []
    
    with open(NGINX_LOG_FILE, 'r', encoding='utf-8') as f:
        f.seek(current_position)
        
        # 读取所有新行
        lines = f.readlines()
        
        for line in lines:
            line = line.strip()
            if not line:
                continue
            
            try:
                log_data = json.loads(line)
                logs.append(log_data)
                
                # 达到批量大小，插入数据库
                if len(logs) >= BATCH_SIZE:
                    insert_logs_to_mysql(logs)
                    logs = []
            except json.JSONDecodeError as e:
                print(f"⚠️ 解析JSON失败: {e}")
                continue
        
        # 插入剩余的日志
        if logs:
            insert_logs_to_mysql(logs)
        
        # 保存读取位置（在循环结束后调用 tell）
        new_position = f.tell()
        save_position(new_position)


def main():
    """主函数"""
    print("=" * 60)
    print("🚀 Nginx日志采集器启动（直接写入MySQL）")
    print("=" * 60)
    print(f"📁 日志文件: {NGINX_LOG_FILE}")
    print(f"💾 数据库: {MYSQL_CONFIG['host']}:{MYSQL_CONFIG['port']}/{MYSQL_CONFIG['database']}")
    print(f"📦 批量大小: {BATCH_SIZE}")
    print(f"⏱️  检查间隔: {CHECK_INTERVAL}秒")
    print(f"🔍 真实IP头优先级: {', '.join(REAL_IP_HEADERS)}")
    print("=" * 60)
    print()
    
    while True:
        try:
            read_new_logs()
        except Exception as e:
            print(f"🚫 读取日志时出错: {e}")
        
        time.sleep(CHECK_INTERVAL)


if __name__ == "__main__":
    main()
