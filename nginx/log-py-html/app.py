# -*- coding: utf-8 -*-
"""
Nginx日志数据大屏后端服务
提供日志统计数据API，支持实时查询访问日志
"""
import json
import time
import threading
from flask import Flask, jsonify, render_template, request
import pymysql
from datetime import datetime, timedelta
from decimal import Decimal

app = Flask(__name__)

# 自定义JSON编码器，处理datetime和Decimal类型
class CustomJSONEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, datetime):
            return obj.strftime('%Y-%m-%d %H:%M:%S')
        elif isinstance(obj, Decimal):
            return float(obj)
        return super().default(obj)

app.json_encoder = CustomJSONEncoder

# 数据库配置
DB_CONFIG = {
    'host': '192.168.3.100',
    'user': 'root',
    'password': '123456',
    'database': 'test',
    'port': 3306,
    'charset': 'utf8mb4'
}

# 缓存数据（减少数据库查询压力）
cache_data = {}
cache_time = 0
CACHE_TTL = 10  # 缓存有效期（秒）

# 获取客户端真实IP
def get_client_ip():
    """从请求中获取客户端真实IP"""
    # 优先从请求参数获取
    client_ip = request.args.get('client_ip', '')
    if client_ip:
        return client_ip
    
    # 从请求头获取
    ip_headers = [
        'X-Client-IP',
        'X-Real-IP',
        'X-Forwarded-For',
        'X-Forwarded',
        'X-ProxyUser-Ip',
        'WL-Proxy-Client-IP',
        'HTTP_CLIENT_IP',
        'HTTP_X_FORWARDED_FOR'
    ]
    
    for header in ip_headers:
        ip = request.headers.get(header, '')
        if ip:
            # X-Forwarded-For可能包含多个IP，取第一个
            if header == 'X-Forwarded-For' and ',' in ip:
                ip = ip.split(',')[0].strip()
            return ip
    
    # 从remote_addr获取
    return request.remote_addr or ''

def log_request_info():
    """记录请求信息（包含客户端IP）"""
    client_ip = get_client_ip()
    print(f"[REQUEST] IP: {client_ip} | Path: {request.path} | Method: {request.method}")

def get_db_connection():
    """获取数据库连接"""
    return pymysql.connect(**DB_CONFIG)

def query_logs_by_time(start_time, end_time, limit=1000):
    """按时间范围查询日志"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor(pymysql.cursors.DictCursor)
        
        sql = """
            SELECT * FROM nginx_access_log 
            WHERE time_local >= %s AND time_local <= %s 
            ORDER BY time_local DESC 
            LIMIT %s
        """
        cursor.execute(sql, (start_time, end_time, limit))
        results = cursor.fetchall()
        
        conn.close()
        return results
    except Exception as e:
        print(f"查询日志失败: {e}")
        return []

def get_statistics():
    """获取日志统计数据"""
    global cache_data, cache_time
    
    # 检查缓存是否有效
    now = time.time()
    if now - cache_time < CACHE_TTL and cache_data:
        return cache_data
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor(pymysql.cursors.DictCursor)
        
        statistics = {}
        
        # 1. 今日访问量
        today_start = datetime.now().strftime('%Y-%m-%d 00:00:00')
        today_end = datetime.now().strftime('%Y-%m-%d 23:59:59')
        cursor.execute("SELECT COUNT(*) as count FROM nginx_access_log WHERE time_local >= %s", (today_start,))
        statistics['today_visits'] = cursor.fetchone()['count']
        
        # 2. 总访问量
        cursor.execute("SELECT COUNT(*) as count FROM nginx_access_log")
        statistics['total_visits'] = cursor.fetchone()['count']
        
        # 3. 今日独立IP数
        cursor.execute("SELECT COUNT(DISTINCT real_ip) as count FROM nginx_access_log WHERE time_local >= %s", (today_start,))
        statistics['today_unique_ips'] = cursor.fetchone()['count']
        
        # 4. 状态码分布
        cursor.execute("""
            SELECT status, COUNT(*) as count 
            FROM nginx_access_log 
            WHERE time_local >= %s 
            GROUP BY status 
            ORDER BY count DESC
        """, (today_start,))
        statistics['status_distribution'] = cursor.fetchall()
        
        # 5. 访问来源TOP10
        cursor.execute("""
            SELECT http_referer, COUNT(*) as count 
            FROM nginx_access_log 
            WHERE time_local >= %s AND http_referer != '' 
            GROUP BY http_referer 
            ORDER BY count DESC 
            LIMIT 10
        """, (today_start,))
        statistics['top_referers'] = cursor.fetchall()
        
        # 6. 用户代理TOP10
        cursor.execute("""
            SELECT http_user_agent, COUNT(*) as count 
            FROM nginx_access_log 
            WHERE time_local >= %s 
            GROUP BY http_user_agent 
            ORDER BY count DESC 
            LIMIT 10
        """, (today_start,))
        statistics['top_user_agents'] = cursor.fetchall()
        
        # 7. 请求路径TOP10
        cursor.execute("""
            SELECT request_uri, COUNT(*) as count 
            FROM nginx_access_log 
            WHERE time_local >= %s 
            GROUP BY request_uri 
            ORDER BY count DESC 
            LIMIT 10
        """, (today_start,))
        statistics['top_requests'] = cursor.fetchall()
        
        # 8. 最近100条日志
        cursor.execute("""
            SELECT * FROM nginx_access_log 
            ORDER BY time_local DESC 
            LIMIT 100
        """)
        statistics['recent_logs'] = cursor.fetchall()
        
        # 9. 小时访问趋势（今日）
#         hours = []
#         for hour in range(24):
#             hour_start = f"{datetime.now().strftime('%Y-%m-%d')} {hour:02d}:00:00"
#             hour_end = f"{datetime.now().strftime('%Y-%m-%d')} {hour:02d}:59:59"
#             cursor.execute("""
#                 SELECT COUNT(*) as count
#                 FROM nginx_access_log
#                 WHERE time_local >= %s AND time_local <= %s
#             """, (hour_start, hour_end))
#             hours.append({'hour': f"{hour:02d}:00", 'count': cursor.fetchone()['count']})
#         statistics['hourly_trend'] = hours


        # 8. 分钟访问趋势（最近60分钟）
        now = datetime.now()
        minutes = []

        for i in range(59, -1, -1):
            minute_start = (now - timedelta(minutes=i)).replace(second=0, microsecond=0)
            minute_end = minute_start + timedelta(minutes=1)

            cursor.execute("""
                SELECT COUNT(*) as count
                FROM nginx_access_log
                WHERE time_local >= %s AND time_local < %s
            """, (minute_start, minute_end))

            minutes.append({
                'time': minute_start.strftime('%H:%M'),
                'count': cursor.fetchone()['count']
            })

        statistics['minute_trend'] = minutes
        
        # 10. 响应时间统计
        cursor.execute("""
            SELECT 
                AVG(request_time) as avg_time,
                MAX(request_time) as max_time,
                MIN(request_time) as min_time
            FROM nginx_access_log
            WHERE time_local >= %s
        """, (today_start,))
        statistics['response_time'] = cursor.fetchone()

        # 11. 今日流量统计
        cursor.execute("""
            SELECT COALESCE(SUM(body_bytes_sent), 0) as total_traffic
            FROM nginx_access_log
            WHERE time_local >= %s
        """, (today_start,))
        statistics['today_traffic'] = cursor.fetchone()['total_traffic']

        conn.close()
        
        # 更新缓存
        cache_data = statistics
        cache_time = time.time()
        
        return statistics
        
    except Exception as e:
        print(f"获取统计数据失败: {e}")
        return {
            'today_visits': 0,
            'total_visits': 0,
            'today_unique_ips': 0,
            'today_traffic': 0,
            'status_distribution': [],
            'top_referers': [],
            'top_user_agents': [],
            'top_requests': [],
            'recent_logs': [],
            'hourly_trend': [],
            'response_time': {'avg_time': 0, 'max_time': 0, 'min_time': 0}
        }

@app.route('/')
def index():
    """大屏页面"""
    log_request_info()
    return render_template('index.html')

@app.route('/api/statistics')
def api_statistics():
    """获取统计数据API"""
    log_request_info()
    data = get_statistics()
    return jsonify(data)

@app.route('/api/logs')
def api_logs():
    """查询日志列表"""
    log_request_info()
    page = int(request.args.get('page', 1))
    limit = int(request.args.get('limit', 50))
    start_time = request.args.get('start_time')
    end_time = request.args.get('end_time')
    status = request.args.get('status')
    ip = request.args.get('ip')
    method = request.args.get('method')

    try:
        conn = get_db_connection()
        cursor = conn.cursor(pymysql.cursors.DictCursor)

        sql = "SELECT * FROM nginx_access_log WHERE 1=1"
        params = []

        if start_time:
            sql += " AND time_local >= %s"
            params.append(start_time)
        if end_time:
            sql += " AND time_local <= %s"
            params.append(end_time)
        if status:
            sql += " AND status = %s"
            params.append(status)
        if ip:
            sql += " AND real_ip LIKE %s"
            params.append(f"%{ip}%")
        if method:
            sql += " AND request_method = %s"
            params.append(method)

        sql += " ORDER BY time_local DESC LIMIT %s OFFSET %s"
        params.extend([limit, (page - 1) * limit])
        
        cursor.execute(sql, params)
        logs = cursor.fetchall()
        
        # 获取总数
        sql_count = sql.replace('SELECT *', 'SELECT COUNT(*) as count').replace('ORDER BY time_local DESC LIMIT %s OFFSET %s', '')
        cursor.execute(sql_count, params[:-2])
        total = cursor.fetchone()['count']
        
        conn.close()
        
        return jsonify({'data': logs, 'total': total, 'page': page, 'limit': limit})
    except Exception as e:
        print(f"查询日志API失败: {e}")
        return jsonify({'data': [], 'total': 0, 'page': page, 'limit': limit})

def convert_datetime(obj):
    """递归转换字典中的datetime和Decimal类型"""
    if isinstance(obj, dict):
        return {k: convert_datetime(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [convert_datetime(item) for item in obj]
    elif isinstance(obj, datetime):
        return obj.strftime('%Y-%m-%d %H:%M:%S')
    elif isinstance(obj, Decimal):
        return float(obj)
    else:
        return obj

@app.route('/api/realtime')
def api_realtime():
    """实时获取最新日志（Server-Sent Events）"""
    log_request_info()
    
    def generate():
        last_id = 0
        while True:
            try:
                conn = get_db_connection()
                cursor = conn.cursor(pymysql.cursors.DictCursor)
                
                cursor.execute("SELECT * FROM nginx_access_log WHERE id > %s ORDER BY id DESC LIMIT 10", (last_id,))
                logs = cursor.fetchall()
                
                if logs:
                    last_id = logs[0]['id']
                    # 转换datetime类型后再序列化
                    logs_converted = convert_datetime(logs)
                    yield f"data: {json.dumps(logs_converted)}\n\n"
                
                conn.close()
                time.sleep(2)
            except Exception as e:
                print(f"实时日志推送失败: {e}")
                time.sleep(5)
    
    return app.response_class(generate(), mimetype='text/event-stream')

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
