import mysql.connector
import time
import threading

connections = []
max_connections = 150  # 根据实际环境调整


def create_connection():
    try:
        conn = mysql.connector.connect(
            host='192.168.3.100',
            user='root',
            password='123456'
        )
        connections.append(conn)
        print(f"[{time.strftime('%H:%M:%S')}] ✅ 成功创建连接 #{len(connections)}")
        return True
    except Exception as e:
        print(f"[{time.strftime('%H:%M:%S')}] ❌ 连接失败: {str(e)}")
        return False


def test_max_connections():
    # 1. 获取当前max_connections
    admin_conn = mysql.connector.connect(
        host='192.168.3.100',
        user='root',
        password='123456'
    )
    cursor = admin_conn.cursor()
    cursor.execute("SHOW VARIABLES LIKE 'max_connections'")
    orig_max = int(cursor.fetchone()[1])
    print(f"\n[初始状态] 当前max_connections = {orig_max}")

    # 2. 创建接近上限的连接
    print("\n[步骤1] 创建连接直到接近上限...")
    while len(connections) < orig_max - 2:
        if not create_connection():
            break
        time.sleep(0.1)

    # 3. 修改max_connections为更小值
    new_max = orig_max - 10
    print(f"\n[步骤2] 将max_connections设置为 {new_max}")
    cursor.execute(f"SET GLOBAL max_connections = {new_max}")

    # 4. 验证新连接是否受限制
    print("\n[验证1] 尝试创建新连接（预期应失败）...")
    time.sleep(1)
    create_connection()  # 应失败（已达新上限）

    # 5. 验证实有连接是否正常
    print("\n[验证2] 检查现有连接状态...")
    valid_count = 0
    for i, conn in enumerate(connections[:5]):  # 检查前5个连接
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT CONNECTION_ID(), USER()")
            result = cursor.fetchone()
            print(f"  连接#{i + 1} [{result[0]}] 仍正常工作: {result[1]}")
            valid_count += 1
        except Exception as e:
            print(f"  连接#{i + 1} 异常: {str(e)}")

    print(f"\n[结果] {valid_count}/{min(5, len(connections))} 个现有连接仍正常工作")

    # 清理
    for conn in connections:
        conn.close()
    admin_conn.close()


if __name__ == "__main__":
    test_max_connections()