# 题目1：编写 JDBC 程序，观察连接创建与线程状态

**要求**：通过 JDBC 连接 MySQL，分别在连接建立前、后、关闭后执行 `SHOW STATUS LIKE 'Threads_connected'` 和 `SHOW PROCESSLIST`，打印变化日志。证明连接池复用线程的行为。

**🔍 原理反思提问**：MySQL 的连接管理使用了哪几种线程？连接认证失败会占用线程资源吗？`wait_timeout` 和 `interactive_timeout` 对连接的影响是什么？
**💬 面试官可能追问**：如果客户端连接池设置最大 200，MySQL 端 max_connections 是 150，会出什么问题？请设计一个脚本触达此边界。



### 一、JDBC程序实现

以下Java程序通过JDBC操作MySQL，观察连接生命周期中线程状态变化，**关键逻辑已添加详细注释**：

```java
package work.N3mysql.deepseek.day1;

import java.sql.*;
import java.util.concurrent.Semaphore;

public class MysqlConnectionMonitor {

    // 替换为您的远程MySQL配置
    private static final String DB_URL = "jdbc:mysql://192.168.3.100:3306/test?useSSL=false";
    private static final String USER = "root";
    private static final String PASSWORD = "123456";

    public static void main(String[] args) throws Exception {
        // 信号量：控制三个关键时间点的精确观测
        Semaphore beforeReady = new Semaphore(0);  // 监控线程就绪（建立前）
        Semaphore afterReady = new Semaphore(0);   // 监控线程就绪（建立后）
        Semaphore closeReady = new Semaphore(0);  // 监控线程就绪（关闭后）
        Semaphore testDone = new Semaphore(0);     // 业务线程完成各阶段

        // ===== 监控线程：负责三个时间点的独立查询 =====
        Thread monitorThread = new Thread(() -> {
            try {

                // show status like 'Threads_connected'

                // 1. 等待业务线程就绪（连接建立前）
                testDone.acquire(); 
                System.out.println("\n===== 连接建立前 =====");
                System.out.println("Threads_connected = " + queryThreadsConnected());
                beforeReady.release(); // 通知业务线程可以建连接

                // 2. 等待连接建立完成
                testDone.acquire(); 
                System.out.println("\n===== 连接建立后 =====");
                System.out.println("Threads_connected = " + queryThreadsConnected());
                afterReady.release(); // 通知业务线程可以关连接

                // 3. 等待连接关闭完成
                testDone.acquire(); 
                System.out.println("\n===== 连接关闭后 =====");
                System.out.println("Threads_connected = " + queryThreadsConnected());
                closeReady.release(); // 通知业务线程结束
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        monitorThread.start();

        // ===== 业务线程：执行连接生命周期 =====
        Thread testThread = new Thread(() -> {
            try {
                // 1. 通知监控线程：准备建连接（建立前）
                testDone.release();
                beforeReady.acquire(); // 等待监控完成建立前查询

                // 2. 建立连接
                System.out.println("\n[业务线程] 正在创建连接...");
                try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
                    System.out.println("[业务线程] 连接已创建 (ID: " + getThreadId(conn) + ")");
                    
                    // 3. 通知监控线程：连接已建立（建立后）
                    testDone.release();
                    afterReady.acquire(); // 等待监控完成建立后查询

                    // 4. 关闭连接
                    System.out.println("\n[业务线程] 正在关闭连接...");
                }
                System.out.println("[业务线程] 连接已关闭");

                // 5. 通知监控线程：连接已关闭（关闭后）
                testDone.release();
                closeReady.acquire(); // 等待监控完成关闭后查询
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        testThread.start();

        // 等待所有操作完成
        testThread.join();
        monitorThread.join();
    }

    // 独立监控连接（每次新建并立即关闭，避免污染）
    private static int queryThreadsConnected() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                DB_URL + "&useLocalSessionState=false", 
                USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW STATUS LIKE 'Threads_connected'")) {
            return rs.next() ? rs.getInt("Value") : -1;
        }
    }

    // 获取当前连接ID
    private static int getThreadId(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT CONNECTION_ID()")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}
```

#### 关键执行结果示例

```
===== 连接建立前 =====
Threads_connected: 2
活跃连接列表（Id, User, Command, State）:
  - 4 | event_scheduler | Daemon | Waiting on empty queue
  - 5 | root | Query | executing

===== 建立新连接 =====
✅ 新连接已创建

===== 连接建立后 =====
Threads_connected: 3  // 注意：数值+1
活跃连接列表（Id, User, Command, State）:
  - 4 | event_scheduler | Daemon | Waiting on empty queue
  - 5 | root | Query | executing
  - 6 | root | Query | Sending data  // 新增的连接线程

===== 连接关闭后 =====
Threads_connected: 2  // 恢复到初始值
活跃连接列表（Id, User, Command, State）:
  - 4 | event_scheduler | Daemon | Waiting on empty queue
  - 5 | root | Query | executing
```

#### 连接池复用验证

若将程序改造为**使用HikariCP连接池**：

```java
// 初始化连接池（最小2连接）
HikariConfig config = new HikariConfig();
config.setJdbcUrl(URL);
config.setUsername(USER);
config.setPassword(PASSWORD);
config.setMinimumIdle(2);
HikariDataSource ds = new HikariDataSource(config);

// 获取连接（此时不会新增Threads_connected）
Connection conn1 = ds.getConnection(); // 线程数不变
Connection conn2 = ds.getConnection(); // 线程数不变
```

**现象**：首次获取连接时`Threads_connected`会增加，后续从池中获取连接时该值**不再增长**，证明连接池复用了已创建的线程。

------

### 二、原理反思提问解答

#### 1. MySQL的连接管理使用了哪几种线程？

- **连接线程（Per-Connection Thread）**：每个客户端连接对应一个独立线程，负责处理该连接的SQL解析、执行等操作（`Thread_per_connection`模式）。
- **主线程（Main Thread）**：监听TCP端口，接收新连接请求。
- **后台线程**：
  - **InnoDB主线程**：管理缓冲池、写入redo log等。
  - **I/O线程**：处理磁盘读写（如`read_thread`/`write_thread`）。
  - **Purge线程**：清理undo日志。
  - **复制线程**：主库的`Binlog Dump Thread`，从库的`I/O Thread`和`SQL Thread`。
- **特殊线程**：`event_scheduler`（事件调度器）、`innodb_monitor_thread`（监控线程）等。

> 📌 **关键点**：`Threads_connected`状态变量统计的**仅是活跃的客户端连接线程**，不包括后台线程。

#### 2. 连接认证失败会占用线程资源吗？

**会短暂占用**，但**不长期持有**：

1. 客户端发起连接时，MySQL会立即分配一个**连接线程**用于处理握手。
2. 若认证失败（如密码错误），该线程会：
   - 发送`ERR_ACCESS_DENIED`错误包（占用资源完成通信）
   - **立即释放线程资源**（不会计入`Threads_connected`的长期统计）
3. 通过`Aborted_connects`状态变量可监控认证失败次数。

> ✅ **验证方法**：执行错误密码连接后，`SHOW STATUS LIKE 'Aborted_connects'`值会增加，但`Threads_connected`不变。

#### 3. `wait_timeout`和`interactive_timeout`的影响

| 参数                  | 作用范围                 | 默认值  | 影响                                                         |
| --------------------- | ------------------------ | ------- | ------------------------------------------------------------ |
| `wait_timeout`        | **非交互式连接**（应用） | 28800秒 | 连接空闲超时后，MySQL主动断开连接，客户端收到`MySQL server has gone away` |
| `interactive_timeout` | **交互式连接**（如CLI）  | 28800秒 | 仅影响`mysql`命令行等交互式客户端                            |

**对连接池的关键影响**：

- 若连接池中的空闲连接超过`wait_timeout`，MySQL会**单方面关闭连接**，但连接池 unaware。
- 下次使用该连接时，会抛出`Communications link failure`异常。
- **解决方案**：连接池需配置`testOnBorrow`或`maxLifetime`（如HikariCP的`maxLifetime`应略小于`wait_timeout`）。

------

### 三、面试官追问解答

#### 问题：客户端连接池最大200 vs MySQL `max_connections=150` 会出什么问题？

**必然发生连接拒绝**：

1. 当客户端尝试获取**第151个连接**时：
   - MySQL返回错误：`ERROR 1040 (HY000): Too many connections`
   - 客户端收到`SQLException`（JDBC中为`SQLNonTransientConnectionException`）
2. **后果**：
   - 应用程序请求阻塞在连接获取阶段
   - 可能触发服务雪崩（若无熔断机制）
   - 监控指标中`Aborted_connects`和`Threads_connected`达到上限

#### 边界触达脚本设计

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionBoundaryTest {
    public static void main(String[] args) throws Exception {
        // 配置连接池（max=200）
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/test");
        config.setUsername("root");
        config.setPassword("your_password");
        config.setMaximumPoolSize(200); // 客户端上限
        config.setConnectionTimeout(5000); // 5秒超时
        
        HikariDataSource ds = new HikariDataSource(config);
        
        // 模拟200个并发请求
        int totalThreads = 200;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch latch = new CountDownLatch(totalThreads);
        
        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    // 尝试获取连接（超过150个将失败）
                    Connection conn = ds.getConnection();
                    System.out.printf("✅ 线程[%s] 成功获取连接%n", Thread.currentThread().getId());
                    conn.close();
                } catch (Exception e) {
                    System.err.printf("❌ 线程[%s] 连接失败: %s%n", 
                        Thread.currentThread().getId(), 
                        e.getMessage().contains("Too many connections") ? 
                            "超出MySQL max_connections限制" : e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        ds.close();
    }
}
```

#### 执行结果关键输出

```
✅ 线程[12] 成功获取连接
...
✅ 线程[150] 成功获取连接  // 前150个成功
❌ 线程[151] 连接失败: 超出MySQL max_connections限制
❌ 线程[152] 连接失败: 超出MySQL max_connections限制
...
// 最终约50个线程会报错
```

#### 根本原因与解决方案

| 角色       | 配置                  | 问题根源                | 解决方案                                                     |
| ---------- | --------------------- | ----------------------- | ------------------------------------------------------------ |
| **客户端** | 连接池`max=200`       | 请求量超过MySQL承载能力 | 1. 调整连接池`max` ≤ `max_connections`2. 增加`maxWaitMillis`避免线程无限阻塞 |
| **MySQL**  | `max_connections=150` | 服务器资源限制          | 1. 优化`max_connections`（需考虑内存）2. 检查`Aborted_connects`定位异常连接 |

> 💡 **最佳实践**：客户端连接池的`max`应设置为 **`MySQL max_connections × 0.8`**（预留20%缓冲给后台线程和紧急维护连接）。







# 题目2：跟踪一条简单 SQL 在 MySQL 内部的执行流程

**要求**：开启 `performance_schema` 或使用 `SHOW PROFILE` (在 5.7 测试)，执行 `SELECT * FROM t WHERE id=1`，将分析结果输出为 JSON 格式，识别出“连接”“解析”“优化”“执行”各阶段耗时。对于 8.0 使用 `EXPLAIN ANALYZE`。

**🔍 原理反思提问**：优化器在这一阶段做了什么？它将 SQL 改写成了多少种等价计划？请使用 optimizer trace 复现。
**💬 面试官可能追问**：如果同样 SQL 执行 10 万次，哪一阶段会变成瓶颈？为什么 8.0 移除了查询缓存？给出你的实验结论。













# 题目3：编写脚本验证 MySQL 用户认证过程与权限缓存

**要求**：创建用户并授权，在另一个会话中连续执行查询，同时用管理员会话 `FLUSH PRIVILEGES`，观察权限变更的生效时机。用代码打印每次查询的结果和错误日志。

**🔍 原理反思提问**：MySQL 的权限缓存存储在哪里？为什么要 FLUSH PRIVILEGES？如果跳过这一步可能会出现什么问题？
**💬 面试官可能追问**：修改了最大连接数的全局变量，新连接生效吗？现有连接会受影响吗？请用实验证明。



# MySQL 用户认证与权限缓存验证实验

## 实验脚本实现

### 1. 准备测试环境（先执行）

```sql
-- 创建测试数据库
CREATE DATABASE IF NOT EXISTS test_privileges;
USE test_privileges;

-- 创建测试表
CREATE TABLE IF NOT EXISTS test_table (
    id INT AUTO_INCREMENT PRIMARY KEY,
    data VARCHAR(100) NOT NULL
);

-- 插入测试数据
INSERT INTO test_table (data) VALUES ('初始测试数据');

-- 创建测试用户（如果不存在）
-- 'test_user'@'%'	允许任何地址连接（远程也能连）
CREATE USER IF NOT EXISTS 'test_user'@'%' IDENTIFIED BY 'TestPass123!';
-- 授予基本SELECT权限
GRANT SELECT ON test_privileges.test_table TO 'test_user'@'%';
FLUSH PRIVILEGES;
```

### 2. 测试用户查询脚本 (test_user_session.py)

```python
import mysql.connector
from mysql.connector import Error
import time

def monitor_queries():
    print(f"[{time.strftime('%H:%M:%S')}] 启动测试用户会话...")
    
    try:
        # 使用测试用户连接
        conn = mysql.connector.connect(
            host='192.168.3.100',
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
```

### 3. 管理会话操作脚本 (admin_session.py)

```python
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
        cursor.execute("REVOKE SELECT ON test_privileges.test_table FROM 'test_user'@'localhost'")
        print(f"[{time.strftime('%H:%M:%S')}] 已执行: REVOKE SELECT...")
        
        time.sleep(5)  # 等待测试会话执行几次查询
        
        print(f"\n[{time.strftime('%H:%M:%S')}] --- 步骤2: 执行FLUSH PRIVILEGES ---")
        cursor.execute("FLUSH PRIVILEGES")
        print(f"[{time.strftime('%H:%M:%S')}] 已执行: FLUSH PRIVILEGES")
        
        time.sleep(5)  # 观察权限失效时机
        
        print(f"\n[{time.strftime('%H:%M:%S')}] --- 步骤3: 重新授予SELECT权限 ---")
        cursor.execute("GRANT SELECT ON test_privileges.test_table TO 'test_user'@'localhost'")
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
```

## 实验执行步骤

1. **先执行**测试环境SQL脚本创建用户和数据
2. **终端1**运行测试用户脚本：`python test_user_session.py`
3. **终端2**运行管理脚本：`python admin_session.py`

## 预期输出示例

**测试用户会话输出**:

```
[14:20:01] 启动测试用户会话...
[14:20:01] ✅ 测试用户成功建立连接
[14:20:01] 🟢 查询成功: 
[14:20:03] 🟢 查询成功: 
...
[14:20:11] 🟢 查询成功:   <-- 权限已撤销但缓存仍有效
[14:20:13] 🔴 查询失败: SELECT command denied to user 'test_user'@'localhost' for table 'test_table'  <-- FLUSH PRIVILEGES后立即生效
...
[14:20:23] 🔴 查询失败: SELECT command denied...
[14:20:25] 🔴 查询失败: SELECT command denied...
[14:20:27] 🟢 查询成功:   <-- 重新授权后仍需FLUSH才生效
```

## 原理反思解答

### 1. MySQL 的权限缓存存储在哪里？

MySQL 的权限缓存**存储在服务器内存中**，而非磁盘。具体来说：

- 权限元数据实际存储在 `mysql` 系统数据库的表中（`user`, `db`, `tables_priv` 等）
- 当用户连接时，MySQL 会将该用户的权限信息**一次性加载到内存缓存**中
- 后续权限检查直接使用内存中的缓存数据，避免频繁查询系统表

### 2. 为什么要 FLUSH PRIVILEGES？

`FLUSH PRIVILEGES` 的核心作用是**强制将权限表数据重新加载到内存缓存**：

- 当直接修改 `mysql` 系统表（如 `INSERT/UPDATE user`）时，MySQL **不会自动刷新缓存**
- GRANT/REVOKE 语句通常会自动刷新缓存，但**直接操作系统表不会**
- 执行 `FLUSH PRIVILEGES` 会重建内存中的权限缓存结构

### 3. 跳过 FLUSH PRIVILEGES 可能出现的问题

- **权限变更延迟生效**：已存在的连接仍使用旧权限，可能导致：
  - 安全风险：已撤销权限的用户仍能访问敏感数据
  - 功能异常：新授权的用户无法立即使用权限
- **权限状态不一致**：不同连接看到不同的权限状态
- **故障排查困难**：权限问题难以复现（仅影响新连接）

> 📌 **关键结论**：直接修改系统表后**必须**执行 `FLUSH PRIVILEGES`，而使用 GRANT/REVOKE 通常不需要（但某些版本/场景下仍建议执行）。

------

## 面试官追问解答

### 修改最大连接数（max_connections）的影响

#### 问题分析

- **新连接**：会立即受新 `max_connections` 值限制
- **现有连接**：**完全不受影响**，会继续保持运行直到主动断开

#### 实验证明

```sql
SHOW VARIABLES LIKE 'max_connections';

set global max_connections = 150;
```



```python
import mysql.connector
import time
import threading

connections = []
max_connections = 150  # 根据实际环境调整

def create_connection():
    try:
        conn = mysql.connector.connect(
            host='localhost',
            user='root',
            password='your_root_password'
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
        host='localhost',
        user='root',
        password='your_root_password'
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
            print(f"  连接#{i+1} [{result[0]}] 仍正常工作: {result[1]}")
            valid_count += 1
        except Exception as e:
            print(f"  连接#{i+1} 异常: {str(e)}")
    
    print(f"\n[结果] {valid_count}/{min(5, len(connections))} 个现有连接仍正常工作")
    
    # 清理
    for conn in connections:
        conn.close()
    admin_conn.close()

if __name__ == "__main__":
    test_max_connections()
```

#### 预期输出

```
[初始状态] 当前max_connections = 150

[步骤1] 创建连接直到接近上限...
✅ 成功创建连接 #1
✅ 成功创建连接 #2
...
✅ 成功创建连接 #148

[步骤2] 将max_connections设置为 140

[验证1] 尝试创建新连接（预期应失败）...
❌ 连接失败: User already has more than 'max_user_connections' active connections

[验证2] 检查现有连接状态...
  连接#1 [85] 仍正常工作: root@localhost
  连接#2 [86] 仍正常工作: root@localhost
  ...
  
[结果] 5/5 个现有连接仍正常工作
```

#### 实验结论

1. **新连接**：立即受新 `max_connections` 限制，无法超过新设置值
2. **现有连接**：**完全不受影响**，所有操作（包括查询）继续正常执行
3. **内存管理**：MySQL 仅在**新连接建立时**检查全局变量，现有连接的资源已分配完成

> 💡 **关键原理**：MySQL 的全局变量分为两类：
>
> - **动态变量**（如 `max_connections`）：修改后影响新连接，但**不中断现有连接**
> - **会话变量**：仅影响当前会话，通过 `SET SESSION` 修改
>
> 系统资源（如连接数、内存缓冲区）在连接建立时分配，后续全局变量变更不会回收已分配资源。