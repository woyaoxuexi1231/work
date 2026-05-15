package work.N1javabasic.v1.day4;

import com.github.benmanes.caffeine.cache.*;
import org.checkerframework.checker.nullness.qual.NonNull;
import java.util.concurrent.*;

/**
 * 使用 Caffeine 实现高并发商品详情缓存
 * 
 * Maven 依赖:
 * <dependency>
 *   <groupId>com.github.ben-manes.caffeine</groupId>
 *   <artifactId>caffeine</artifactId>
 *   <version>3.1.6</version>
 * </dependency>
 * 
 * 场景: 商品详情接口 QPS >10万，需高命中率与无阻塞刷新
 */
public class CaffeineCacheDemo {

    // 模拟数据库查询延迟
    static final int DB_LOAD_DELAY_MS = 200;
    static final ConcurrentHashMap<Long, String> mockDB = new ConcurrentHashMap<>();

    static {
        // 预置两条商品数据
        mockDB.put(2001L, "iPhone 15 Pro 详细信息");
        mockDB.put(2002L, "MacBook Pro M3 详细信息");
    }

    public static void main(String[] args) throws Exception {
        // 异步加载缓存
        AsyncLoadingCache<Long, String> productCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)   // 写入后10分钟过期
                .refreshAfterWrite(1, TimeUnit.MINUTES)   // 写入后1分钟异步刷新（非阻塞）
                .recordStats()                            // 开启详细统计
                .buildAsync(new CacheLoader<Long, String>() {
                    @Override
                    public @NonNull String load(@NonNull Long productId) throws Exception {
                        return loadFromDB(productId);
                    }
                });

        // 模拟高并发商品详情查询
        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 10; i++) {
            final long productId = i % 2 == 0 ? 2001L : 2002L; // 交替访问两个热门商品
            executor.submit(() -> {
                try {
                    // 异步获取，非阻塞
                    CompletableFuture<String> future = productCache.get(productId);
                    String detail = future.get(300, TimeUnit.MILLISECONDS); // 设置超时
                    System.out.printf("线程[%s] 获取商品 %d -> %s%n",
                            Thread.currentThread().getName(), productId, detail);
                } catch (Exception e) {
                    System.err.println("查询超时或异常: " + e.getMessage());
                }
            });
            Thread.sleep(30); // 模拟请求间隔
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        // 输出统计信息
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = productCache.synchronous().stats();
        System.out.printf("\nCaffeine 缓存统计: 命中率=%.2f%%, 加载成功次数=%d, 加载耗时平均=%.2fms%n",
                stats.hitRate() * 100, stats.loadSuccessCount(), stats.averageLoadPenalty() / 1_000_000.0);
    }

    private static String loadFromDB(Long productId) {
        sleep(DB_LOAD_DELAY_MS); // 模拟 DB 延迟
        String data = mockDB.getOrDefault(productId, "商品不存在或已下架");
        System.out.println(" >>> 触发数据库加载, productId=" + productId);
        return data;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}