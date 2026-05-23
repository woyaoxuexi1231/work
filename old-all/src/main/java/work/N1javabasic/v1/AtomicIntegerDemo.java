package work.N1javabasic.v1;

import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIntegerDemo {

    // 全局自增ID生成器
    private static final AtomicInteger idGenerator = new AtomicInteger(0);

    // 请求限流计数器
    private static final AtomicInteger requestCount = new AtomicInteger(0);
    private static final int MAX_REQUEST = 5; // 每秒最多5个请求

    public static void main(String[] args) throws InterruptedException {
        // 1. 自增ID
        for (int i = 0; i < 3; i++) {
            System.out.println("生成ID: " + idGenerator.incrementAndGet());
        }

        // 2. 计数器（多线程统计）
        AtomicInteger counter = new AtomicInteger(0);
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    counter.incrementAndGet(); // 线程安全的 ++i
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        System.out.println("累计计数: " + counter.get()); // 应为 1000

        // 3. 简单限流判断
        System.out.println("==== 限流测试 ====");
        for (int i = 0; i < 8; i++) {
            if (requestCount.incrementAndGet() <= MAX_REQUEST) {
                System.out.println("处理请求 " + requestCount.get());
            } else {
                System.out.println("请求被限流，当前计数: " + requestCount.get());
            }
        }
        // 实际项目中会配合定时重置，这里仅演示原子计数
    }
}