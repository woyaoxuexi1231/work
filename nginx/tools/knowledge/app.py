# -*- coding: utf-8 -*-
"""
知识库服务 - 提供 MD 解析知识的分页列表和随机题目功能
"""
import json
from datetime import datetime
from decimal import Decimal
from flask import Flask, jsonify, render_template, request
import pymysql
import pymysql.cursors

app = Flask(__name__)

# 自定义JSON编码器
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


def get_db_connection():
    """获取数据库连接"""
    return pymysql.connect(**DB_CONFIG)


def log_request():
    """记录请求"""
    print(f"[KNOWLEDGE] {request.method} {request.path}")


@app.route('/')
def index():
    """知识列表页"""
    log_request()
    return render_template('list.html')


@app.route('/random')
def random_page():
    """随机题目页"""
    log_request()
    return render_template('random.html')


@app.route('/api/categories')
def api_categories():
    """获取所有分类列表"""
    log_request()
    try:
        conn = get_db_connection()
        cursor = conn.cursor(pymysql.cursors.DictCursor)
        cursor.execute("""
            SELECT category, COUNT(*) as count
            FROM knowledge
            WHERE is_deleted = 0
            GROUP BY category
            ORDER BY category
        """)
        categories = cursor.fetchall()
        conn.close()
        return jsonify(categories)
    except Exception as e:
        print(f"获取分类失败: {e}")
        return jsonify([])


@app.route('/api/list')
def api_list():
    """分页查询题目列表"""
    log_request()
    page = int(request.args.get('page', 1))
    page_size = int(request.args.get('pageSize', 20))
    category = request.args.get('category', '')

    if page < 1:
        page = 1
    if page_size < 1 or page_size > 100:
        page_size = 20

    offset = (page - 1) * page_size

    try:
        conn = get_db_connection()
        cursor = conn.cursor(pymysql.cursors.DictCursor)

        where = "WHERE is_deleted = 0"
        params = []

        if category:
            where += " AND category = %s"
            params.append(category)

        # 查询总数
        count_sql = f"SELECT COUNT(*) as total FROM knowledge {where}"
        cursor.execute(count_sql, params)
        total = cursor.fetchone()['total']

        # 查询列表（只返回 title 和摘要，不返回完整 content 以加快速度）
        list_sql = f"""
            SELECT id, category, title,
                   LEFT(content, 200) as summary,
                   create_time
            FROM knowledge
            {where}
            ORDER BY category, id
            LIMIT %s OFFSET %s
        """
        cursor.execute(list_sql, params + [page_size, offset])
        items = cursor.fetchall()

        conn.close()

        return jsonify({
            'data': items,
            'total': total,
            'page': page,
            'pageSize': page_size,
            'totalPages': (total + page_size - 1) // page_size if total > 0 else 0
        })
    except Exception as e:
        print(f"查询列表失败: {e}")
        return jsonify({'data': [], 'total': 0, 'page': page, 'pageSize': page_size, 'totalPages': 0})


@app.route('/api/detail')
def api_detail():
    """获取单条题目详情"""
    log_request()
    knowledge_id = request.args.get('id', type=int)
    if not knowledge_id:
        return jsonify({'error': '缺少 id 参数'}), 400

    try:
        conn = get_db_connection()
        cursor = conn.cursor(pymysql.cursors.DictCursor)
        cursor.execute(
            "SELECT id, category, title, content, create_time FROM knowledge WHERE id = %s AND is_deleted = 0",
            (knowledge_id,)
        )
        item = cursor.fetchone()
        conn.close()

        if item:
            return jsonify(item)
        return jsonify({'error': '未找到'}), 404
    except Exception as e:
        print(f"查询详情失败: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/api/random')
def api_random():
    """获取随机一道题目"""
    log_request()
    try:
        conn = get_db_connection()
        cursor = conn.cursor(pymysql.cursors.DictCursor)

        category = request.args.get('category', '')

        if category:
            cursor.execute(
                "SELECT id, category, title, content, create_time FROM knowledge WHERE is_deleted = 0 AND category = %s ORDER BY RAND() LIMIT 1",
                (category,)
            )
        else:
            cursor.execute(
                "SELECT id, category, title, content, create_time FROM knowledge WHERE is_deleted = 0 ORDER BY RAND() LIMIT 1"
            )

        item = cursor.fetchone()
        conn.close()

        if item:
            return jsonify(item)
        return jsonify({'error': '暂无数据'}), 404
    except Exception as e:
        print(f"获取随机题目失败: {e}")
        return jsonify({'error': str(e)}), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5011, debug=False, threaded=True)
