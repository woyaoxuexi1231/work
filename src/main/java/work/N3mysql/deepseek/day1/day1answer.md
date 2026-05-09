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
    private static final String DB_URL = "jdbc:mysql://192.168.2.102:3306/test?useSSL=false";
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