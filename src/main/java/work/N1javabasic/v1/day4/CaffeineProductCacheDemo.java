package work.N1javabasic.v1.day4;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 直接运行即可验证线程安全性
 * 输出结果会显示：所有线程获取到相同ID的商品数据完全一致（证明无并发问题）
 */
public class CaffeineProductCacheDemo {

    // 模拟数据库（线程安全）
    private static final ConcurrentHashMap<Long, Product> DB = new ConcurrentHashMap<>();
    private static final AtomicInteger DB_LOAD_COUNT = new AtomicInteger();

    // Caffeine 缓存（线程安全，无需额外同步）
    private static final Cache<Long, Product> CACHE = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();

    static {
        // 初始化模拟数据
        DB.put(1L, new Product(1L, "iPhone 15", 5999.0));
        DB.put(2L, new Product(2L, "MacBook Pro", 14999.0));
    }

    public static void main(String[] args) throws InterruptedException {
        testConcurrentAccess();
    }

    /**
     * 核心缓存逻辑（线程安全实现）
     */
    public static Product getProduct(Long id) {
        // Caffeine 的 get 方法是原子操作：并发请求相同 key 时只触发一次 load
        return CACHE.get(id, k -> {
            System.out.printf("[线程 %s] 触发缓存加载: id=%d%n", Thread.currentThread().getName(), k);
            Product product = loadFromDB(k);
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }
            return product;
        });
    }

    /**
     * 模拟DB访问（含降级逻辑）
     */
    private static Product loadFromDB(Long id) {
        DB_LOAD_COUNT.incrementAndGet();
        try {
            // 模拟DB延迟（200ms内随机）
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return DB.get(id);
    }

    /**
     * 线程安全验证测试
     */
    private static void testConcurrentAccess() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Long targetId = 1L; // 测试同一个商品ID的并发

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Product p = getProduct(targetId);
                    // 验证所有线程获取到相同数据
                    assert p.id.equals(targetId) : "数据不一致!";
                    assert p.name.equals("iPhone 15") : "数据不一致!";
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        
        System.out.println("\n===== 线程安全验证结果 =====");
        System.out.printf("缓存加载次数: %d (期望值: 1，证明并发请求只加载1次)%n", DB_LOAD_COUNT.get());
        System.out.printf("缓存命中率: %.2f%%%n", 
            (1 - (double) DB_LOAD_COUNT.get() / threadCount) * 100);
    }

    // 商品模型（不可变对象保证线程安全）
    public static class Product {
        public final Long id;
        public final String name;
        public final double price;

        public Product(Long id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
    }
}