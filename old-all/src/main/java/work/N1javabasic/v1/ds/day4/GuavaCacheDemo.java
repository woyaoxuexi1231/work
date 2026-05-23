package work.N1javabasic.v1.ds.day4;

import com.google.common.cache.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用 Guava Cache 实现用户信息缓存
 * 
 * Maven 依赖:
 * <dependency>
 *   <groupId>com.google.guava</groupId>
 *   <artifactId>guava</artifactId>
 *   <version>31.1-jre</version>
 * </dependency>
 * 
 * 场景: 高并发用户查询，需自动加载、过期与统计
 */
public class GuavaCacheDemo {

    // 模拟数据库
    static final AtomicInteger dbLoadCount = new AtomicInteger(0);

    public static void main(String[] args) {
        LoadingCache<Long, String> userCache = CacheBuilder.newBuilder()
                .maximumSize(100)                        // 最大条目数
                .expireAfterWrite(5, TimeUnit.SECONDS)   // 写入后5秒过期（演示用）
                .recordStats()                           // 开启统计
                .build(new CacheLoader<Long, String>() {
                    @Override
                    public String load(Long userId) {    // 缓存未命中时自动加载
                        return loadFromDB(userId);
                    }
                });

        // 模拟多次并发查询（单线程演示）
        try {
            for (int i = 0; i < 5; i++) {
                long userId = i % 3 == 0 ? 1001 : 1002; // 交替访问两个用户
                String userInfo = userCache.get(userId);
                System.out.printf("查询用户 %d -> %s (数据库加载总次数: %d)%n",
                        userId, userInfo, dbLoadCount.get());
                Thread.sleep(500);  // 模拟请求间隔
            }
            // 等待6秒，让缓存过期
            System.out.println("\n等待6秒，数据过期...");
            Thread.sleep(6000);
            String userInfo = userCache.get(1001L);
            System.out.printf("过期后查询用户 1001 -> %s (数据库加载总次数: %d)%n",
                    userInfo, dbLoadCount.get());

            // 查看统计信息
            CacheStats stats = userCache.stats();
            System.out.printf("缓存统计: 命中率=%.2f%%, 加载耗时平均值=%.2fms%n",
                    stats.hitRate() * 100, stats.averageLoadPenalty() / 1_000_000.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String loadFromDB(Long userId) {
        dbLoadCount.incrementAndGet();
        sleep(100); // 模拟数据库查询延迟
        return "UserInfo_" + userId;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}