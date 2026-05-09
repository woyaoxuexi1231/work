package work.N1javabasic.old.day14;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class DistributedLeakyBucketDemo {
    public static void main(String[] args) throws InterruptedException {
        // 桶容量 50 个请求，漏出速率每秒 10 个请求（即 100ms 处理一个）
        DistributedLeakyBucket bucket = new DistributedLeakyBucket(
                "192.168.2.102",
                6379,
                "rate_limiter:leaky_bucket",
                50,   // capacity，一个桶最大就能装50个请求
                10.0  // leak rate (req/s) 这个水龙头，一秒处理10个请求
        );

        final int threadCount = 20;
        final long testDurationMs = 10_000;

        long start = System.currentTimeMillis();
        AtomicLong totalAttempts = new AtomicLong(0);
        AtomicLong totalSuccess = new AtomicLong(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    long startTime = System.currentTimeMillis();
                    long attempts = 0, success = 0;
                    while (System.currentTimeMillis() - startTime < testDurationMs) {
                        attempts++;
                        if (bucket.tryAcquire()) {
                            success++;
                        }
                        // 不加额外延迟，尽可能快地发请求
                    }
                    totalAttempts.addAndGet(attempts);
                    totalSuccess.addAndGet(success);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    stopLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        stopLatch.await();

        long attempts = totalAttempts.get();
        long success = totalSuccess.get();
        double qps = success / (testDurationMs / 1000.0);
        double rejectRate = (attempts - success) * 100.0 / attempts;

        System.out.printf("总请求: %d, 成功: %d, 拒绝: %d%n", attempts, success, attempts - success);
        System.out.printf("实际处理 QPS: %.2f, 拒绝率: %.2f%%%n", qps, rejectRate);
        System.out.println("测试耗时: " + (System.currentTimeMillis() - start) + "ms");

        bucket.close();
    }
}