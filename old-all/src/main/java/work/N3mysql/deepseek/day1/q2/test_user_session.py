import mysql.connector
from mysql.connector import Error
import time

def monitor_queries():
    print(f"[{time.strftime('%H:%M:%S')}] 启动测试用户会话...")

    try:
        # 使用测试用户连接
        conn = mysql.connector.connect(
            host='192.168.2.102',
            user='test_user',
            password='TestPass123!',
            database='test_privileges',
            autocommit=True
        )
        cursor = conn.cursor()
        print(f"[{time.strftime('%H:%M:%S')}] ✅ 测试用户成功建立连接")

        # 持续查询30秒
        for i in range(15):
            try:
                cursor.execute("SELECT * FROM test_table")
                result = cursor.fetchall()
                print(f"[{time.strftime('%H:%M:%S')}] 🟢 查询成功: {result}")
            except Error as e:
                print(f"[{time.strftime('%H:%M:%S')}] 🔴 查询失败: {e.msg}")

            time.sleep(2)

    except Error as e:
        print(f"[{time.strftime('%H:%M:%S')}] ❌ 连接失败: {e.msg}")
    finally:
        if 'conn' in locals() and conn.is_connected():
            cursor.close()
            conn.close()
            print(f"[{time.strftime('%H:%M:%S')}] 会话结束")

if __name__ == "__main__":
    monitor_queries()