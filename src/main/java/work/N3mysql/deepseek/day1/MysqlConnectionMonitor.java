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