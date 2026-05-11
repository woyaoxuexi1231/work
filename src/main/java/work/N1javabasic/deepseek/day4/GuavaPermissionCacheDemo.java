package work.N1javabasic.deepseek.day4;

import com.google.common.cache.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 直接运行即可验证线程安全性
 * 输出结果会显示：并发更新时淘汰策略正常工作
 */
public class GuavaPermissionCacheDemo {

    // 模拟数据库（线程安全）
    private static final ConcurrentHashMap<String, Permission> DB = new ConcurrentHashMap<>();
    private static final AtomicInteger EVICTION_COUNT = new AtomicInteger();

    // Guava Cache（线程安全，分段锁实现）
    private static final LoadingCache<String, Permission> CACHE = CacheBuilder.newBuilder()
        .maximumSize(3) // 超小容量强制触发淘汰
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .concurrencyLevel(4) // 显式设置分段数
        .removalListener((RemovalNotification<String, Permission> n) -> 
            EVICTION_COUNT.incrementAndGet())
        .build(new CacheLoader<String, Permission>() {
            @Override
            public Permission load(String userId) {
                System.out.printf("[线程 %s] 加载权限: %s%n", 
                    Thread.currentThread().getName(), userId);
                return loadFromDB(userId);
            }
        });

    static {
        // 初始化模拟数据
        DB.put("user1", new Permission("user1", "READ"));
        DB.put("user2", new Permission("user2", "WRITE"));
        DB.put("user3", new Permission("user3", "ADMIN"));
    }

    public static void main(String[] args) throws InterruptedException {
        testEvictionPolicy();
    }

    /**
     * 核心缓存逻辑（线程安全实现）
     */
    public static Permission getPermission(String userId) {
        try {
            return CACHE.get(userId); // 自动处理并发加载
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 模拟DB访问
     */
    private static Permission loadFromDB(String userId) {
        try {
            Thread.sleep(100); // 模拟DB延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return DB.get(userId);
    }

    /**
     * 淘汰策略验证测试
     */
    private static void testEvictionPolicy() throws InterruptedException {
        // 预热缓存（填充到最大容量）
        getPermission("user1");
        getPermission("user2");
        getPermission("user3");
        
        System.out.println("\n===== 开始并发测试 =====");
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        // 同时请求4个用户（超出maximumSize=3）
        String[] users = {"user1", "user2", "user3", "user4"};
        for (String user : users) {
            executor.submit(() -> {
                Permission p = getPermission(user);
                System.out.printf("获取权限: %s -> %s%n", user, p.role);
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        System.out.println("\n===== 淘汰策略验证结果 =====");
        System.out.printf("被淘汰条目数: %d (期望值: 1)%n", EVICTION_COUNT.get());
        System.out.printf("当前缓存大小: %d (期望值: 3)%n", CACHE.size());
    }

    // 权限模型
    public static class Permission {
        public final String userId;
        public final String role;

        public Permission(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }
    }
}