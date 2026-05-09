package work.N1javabasic.old.day14;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;
import org.apache.commons.io.IOUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

// ================== 测试代码 ==================
public class DistributedRateLimiterDemo {
    public static void main(String[] args) throws InterruptedException {
        // 桶容量 200 令牌，每秒生成 100 令牌
        DistributedTokenBucket bucket = new DistributedTokenBucket(
                "192.168.2.102",
                6379,
                "rate_limiter:test_bucket",
                200, // 桶容量
                100); // 每秒生成令牌数

        final int threadCount = 100;
        final long testDurationMs = 10_000;

        long start = System.currentTimeMillis();

        AtomicLong totalAttempts = new AtomicLong(0);
        AtomicLong totalSuccess = new AtomicLong(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    long startTime = System.currentTimeMillis();
                    long attempts = 0, success = 0;
                    while (System.currentTimeMillis() - startTime < testDurationMs) {
                        attempts++;
                        if (bucket.tryAcquire()) {
                            success++;
                        }
                    }
                    totalAttempts.addAndGet(attempts);
                    totalSuccess.addAndGet(success);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    stopLatch.countDown();
                }
            });
            t.start();
        }

        startLatch.countDown();
        stopLatch.await();

        long attempts = totalAttempts.get();
        long success = totalSuccess.get();
        long reject = attempts - success;
        double qps = success / (testDurationMs / 1000.0);
        double rejectRate = (reject * 100.0) / attempts;

        System.out.printf("总请求: %d, 成功: %d, 拒绝: %d%n", attempts, success, reject);
        System.out.printf("实际 QPS: %.2f, 拒绝率: %.2f%%%n", qps, rejectRate);

        long end = System.currentTimeMillis();
        System.out.println("测试结束，耗时: " + (end - start) + "ms");

        bucket.close();
    }
}