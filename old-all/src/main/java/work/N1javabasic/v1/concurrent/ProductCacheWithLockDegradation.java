package work.N1javabasic.v1.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 商品缓存服务 —— 演示读写锁的锁降级
 */
public class ProductCacheWithLockDegradation {

    // 模拟缓存
    private final Map<String, String> cache = new HashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * 获取商品信息。
     * 如果缓存未命中，则使用写锁加载，并降级为读锁后返回。
     */
    public String getProduct(String id) {
        String productName;
        // 先尝试用读锁获取
        rwLock.readLock().lock();
        try {
            productName = cache.get(id);
            if (productName != null) {
                System.out.println(Thread.currentThread().getName() + " 命中缓存: " + id);
                return productName;
            }
        } finally {
            rwLock.readLock().unlock();
        }

        // 缓存未命中，用写锁加载
        rwLock.writeLock().lock();
        try {
            // 双重检查，因为可能有其他线程刚写入了
            productName = cache.get(id);
            if (productName == null) {
                // 模拟从数据库加载（耗时1秒）
                System.out.println(Thread.currentThread().getName() + " 从DB加载: " + id);
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                productName = "商品-" + id;
                cache.put(id, productName);
            }

            // ★ 锁降级：获取读锁后再释放写锁
            rwLock.readLock().lock();
            System.out.println(Thread.currentThread().getName() + " 完成写锁降级，保持读锁返回数据");
        } finally {
            rwLock.writeLock().unlock(); // 释放写锁，但读锁依然持有
        }

        // 在持有读锁的情况下返回数据
        try {
            return productName;
        } finally {
            rwLock.readLock().unlock(); // 最终释放读锁
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ProductCacheWithLockDegradation cache = new ProductCacheWithLockDegradation();

        // 启动三个线程并发获取同一个商品
        for (int i = 0; i < 3; i++) {
            Thread t = new Thread(() -> {
                String product = cache.getProduct("1001");
                System.out.println(Thread.currentThread().getName() + " 最终获取到: " + product);
            }, "Thread-" + i);
            t.start();
        }
    }
}