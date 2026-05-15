package work.N3mysql.deepseek.day1.q1;

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
        config.setJdbcUrl("jdbc:mysql://192.168.2.102:3306/test");
        config.setUsername("root");
        config.setPassword("123456");
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