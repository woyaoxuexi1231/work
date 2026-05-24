import mysql.connector
from mysql.connector import Error
import time

def admin_operations():
    print(f"[{time.strftime('%H:%M:%S')}] 启动管理员会话...")

    try:
        # 使用管理员连接（请替换实际密码）
        conn = mysql.connector.connect(
            host='192.168.3.100',
            user='root',
            password='123456',
            database='test_privileges'
        )
        cursor = conn.cursor()
        print(f"[{time.strftime('%H:%M:%S')}] ✅ 管理员成功建立连接")

        # 操作步骤
        time.sleep(3)

        print(f"\n[{time.strftime('%H:%M:%S')}] --- 步骤1: 撤销SELECT权限 ---")
        cursor.execute("REVOKE SELECT ON test_privileges.test_table FROM 'test_user'@'%'")
        print(f"[{time.strftime('%H:%M:%S')}] 已执行: REVOKE SELECT...")

        time.sleep(5)  # 等待测试会话执行几次查询

        print(f"\n[{time.strftime('%H:%M:%S')}] --- 步骤2: 执行FLUSH PRIVILEGES ---")
        cursor.execute("FLUSH PRIVILEGES")
        print(f"[{time.strftime('%H:%M:%S')}] 已执行: FLUSH PRIVILEGES")

        time.sleep(5)  # 观察权限失效时机

        print(f"\n[{time.strftime('%H:%M:%S')}] --- 步骤3: 重新授予SELECT权限 ---")
        cursor.execute("GRANT SELECT ON test_privileges.test_table TO 'test_user'@'%'")
        print(f"[{time.strftime('%H:%M:%S')}] 已执行: GRANT SELECT...")

        time.sleep(5)  # 等待测试会话执行查询

        print(f"\n[{time.strftime('%H:%M:%S')}] --- 步骤4: 再次FLUSH PRIVILEGES ---")
        cursor.execute("FLUSH PRIVILEGES")
        print(f"[{time.strftime('%H:%M:%S')}] 已执行: FLUSH PRIVILEGES")

    except Error as e:
        print(f"[{time.strftime('%H:%M:%S')}] ❌ 操作失败: {e.msg}")
    finally:
        if 'conn' in locals() and conn.is_connected():
            cursor.close()
            conn.close()
            print(f"\n[{time.strftime('%H:%M:%S')}] 管理会话结束")

if __name__ == "__main__":
    admin_operations()