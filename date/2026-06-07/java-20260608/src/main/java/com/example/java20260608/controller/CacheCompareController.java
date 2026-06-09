package com.example.java20260608.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.cache.CacheBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/compare")
public class CacheCompareController {

    private static final Logger log = LoggerFactory.getLogger(CacheCompareController.class);
    private static final int POOL_SIZE = Math.max(16, Runtime.getRuntime().availableProcessors() * 2);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(POOL_SIZE);

    @GetMapping("/hitRate")
    public Map<String, Object> hitRate(
        @RequestParam(value = "maxSize", defaultValue = "100000") int maxSize,
        @RequestParam(value = "ops", defaultValue = "200000") int ops,
        @RequestParam(value = "hotKeys", defaultValue = "10000") int hotKeys,
        @RequestParam(value = "hotRatio", defaultValue = "0.8") double hotRatio,
        @RequestParam(value = "valueBytes", defaultValue = "256") int valueBytes,
        @RequestParam(value = "seed", defaultValue = "1") long seed
    ) {
        POOL.execute(() -> runHitRate(maxSize, ops, hotKeys, hotRatio, valueBytes, seed));
        return submitted("命中率", maxSize, ops, hotKeys, hotRatio, valueBytes, seed);
    }

    @GetMapping("/memory")
    public Map<String, Object> memory(
        @RequestParam(value = "entries", defaultValue = "50000") int entries,
        @RequestParam(value = "valueBytes", defaultValue = "1024") int valueBytes
    ) {
        POOL.execute(() -> runMemory(entries, valueBytes));
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("状态", "已提交(后台执行)");
        m.put("对比项", "内存占用(粗略估算)");
        m.put("写入条目数", entries);
        m.put("每条值字节数", valueBytes);
        m.put("提示", "结果打印在后台日志");
        return m;
    }

    @GetMapping("/throughput")
    public Map<String, Object> throughput(
        @RequestParam(value = "maxSize", defaultValue = "100000") int maxSize,
        @RequestParam(value = "threads", defaultValue = "8") int threads,
        @RequestParam(value = "seconds", defaultValue = "3") int seconds,
        @RequestParam(value = "hotKeys", defaultValue = "10000") int hotKeys,
        @RequestParam(value = "hotRatio", defaultValue = "0.8") double hotRatio,
        @RequestParam(value = "valueBytes", defaultValue = "256") int valueBytes,
        @RequestParam(value = "seed", defaultValue = "1") long seed
    ) {
        POOL.execute(() -> runThroughput(maxSize, threads, seconds, hotKeys, hotRatio, valueBytes, seed));
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("状态", "已提交(后台执行)");
        m.put("对比项", "吞吐量(ops/s)");
        m.put("最大容量", maxSize);
        m.put("线程数", threads);
        m.put("持续秒数", seconds);
        m.put("热点key数", hotKeys);
        m.put("热点比例", hotRatio);
        m.put("每条值字节数", valueBytes);
        m.put("随机种子", seed);
        m.put("提示", "结果打印在后台日志");
        return m;
    }

    private static Map<String, Object> submitted(
        String item,
        int maxSize,
        int ops,
        int hotKeys,
        double hotRatio,
        int valueBytes,
        long seed
    ) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("状态", "已提交(后台执行)");
        m.put("对比项", item);
        m.put("最大容量", maxSize);
        m.put("操作次数", ops);
        m.put("热点key数", hotKeys);
        m.put("热点比例", hotRatio);
        m.put("每条值字节数", valueBytes);
        m.put("随机种子", seed);
        m.put("提示", "结果打印在后台日志");
        return m;
    }

    private void runHitRate(int maxSize, int ops, int hotKeys, double hotRatio, int valueBytes, long seed) {
        Cache<String, byte[]> caffeine = Caffeine.newBuilder()
            .maximumSize(maxSize)
            .recordStats()
            .build();

        com.google.common.cache.Cache<String, byte[]> guava = CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .recordStats()
            .build();

        Random rnd = new Random(seed);
        for (int i = 0; i < ops; i++) {
            final int salt = i;
            String key = pickKey(rnd, hotKeys, hotRatio);
            caffeine.get(key, k -> newValue(valueBytes, k, salt));
            guavaGet(guava, key, () -> newValue(valueBytes, key, salt));
        }

        CacheStats c = caffeine.stats();
        com.google.common.cache.CacheStats g = guava.stats();

        log.info("【命中率对比】最大容量={}, 操作次数={}, 热点key数={}, 热点比例={}, 每条值字节数={}, 随机种子={}", maxSize, ops, hotKeys, hotRatio, valueBytes, seed);
        log.info("【命中率对比】Caffeine：请求数={}, 命中率={}, 淘汰数={}, 当前大小(估算)={}", c.requestCount(), c.hitRate(), c.evictionCount(), caffeine.estimatedSize());
        log.info("【命中率对比】Guava   ：请求数={}, 命中率={}, 淘汰数={}, 当前大小={}", g.requestCount(), g.hitRate(), g.evictionCount(), guava.size());
    }

    private void runThroughput(int maxSize, int threads, int seconds, int hotKeys, double hotRatio, int valueBytes, long seed) {
        Cache<String, byte[]> caffeine = Caffeine.newBuilder()
            .maximumSize(maxSize)
            .recordStats()
            .build();

        com.google.common.cache.Cache<String, byte[]> guava = CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .recordStats()
            .build();

        ThroughputResult caffeineR = measureThroughput("Caffeine", threads, seconds, seed, hotKeys, hotRatio, valueBytes, (k, i) -> {
            caffeine.get(k, kk -> newValue(valueBytes, kk, i));
            return null;
        });

        ThroughputResult guavaR = measureThroughput("Guava", threads, seconds, seed, hotKeys, hotRatio, valueBytes, (k, i) -> {
            guavaGet(guava, k, () -> newValue(valueBytes, k, i));
            return null;
        });

        log.info("【吞吐量对比】最大容量={}, 线程数={}, 持续秒数={}, 热点key数={}, 热点比例={}, 每条值字节数={}, 随机种子={}",
            maxSize, threads, seconds, hotKeys, hotRatio, valueBytes, seed);
        log.info("【吞吐量对比】Caffeine：总操作数={}, 耗时毫秒={}, 吞吐量(ops/s)={}", caffeineR.ops, caffeineR.durationMs, caffeineR.opsPerSec);
        log.info("【吞吐量对比】Guava   ：总操作数={}, 耗时毫秒={}, 吞吐量(ops/s)={}", guavaR.ops, guavaR.durationMs, guavaR.opsPerSec);
    }

    private ThroughputResult measureThroughput(
        String name,
        int threads,
        int seconds,
        long seed,
        int hotKeys,
        double hotRatio,
        int valueBytes,
        Work work
    ) {
        int workerThreads = Math.max(1, Math.min(threads, POOL_SIZE - 1));
        long durationNanos = TimeUnit.SECONDS.toNanos(Math.max(1, seconds));
        long end = System.nanoTime() + durationNanos;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workerThreads);
        AtomicLong ops = new AtomicLong(0);

        for (int t = 0; t < workerThreads; t++) {
            final int idx = t;
            POOL.execute(() -> {
                try {
                    Random rnd = new Random(seed + idx);
                    start.await();
                    long i = 0;
                    while (System.nanoTime() < end) {
                        String key = pickKey(rnd, hotKeys, hotRatio);
                        work.apply(key, (int) i);
                        ops.incrementAndGet();
                        i++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        long beginMs = System.currentTimeMillis();
        start.countDown();
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long durationMs = Math.max(1, System.currentTimeMillis() - beginMs);
        long totalOps = ops.get();
        long opsPerSec = (totalOps * 1000L) / durationMs;

        log.info("【吞吐量阶段】{}：实际线程数={}, 请求线程数={}, 持续秒数={}, 热点key数={}, 热点比例={}, 每条值字节数={}", name, workerThreads, threads, seconds, hotKeys, hotRatio, valueBytes);
        return new ThroughputResult(totalOps, durationMs, opsPerSec);
    }

    private void runMemory(int entries, int valueBytes) {
        log.info("【内存占用对比】开始：写入条目数={}, 每条值字节数={}", entries, valueBytes);

        long base = usedMemoryBytes();

        Cache<String, byte[]> caffeine = Caffeine.newBuilder()
            .maximumSize(entries)
            .recordStats()
            .build();
        for (int i = 0; i < entries; i++) {
            String k = "k" + i;
            caffeine.put(k, newValue(valueBytes, k, i));
        }
        long afterCaffeine = usedMemoryBytes();

        caffeine.invalidateAll();
        caffeine = null;
        forceGc();
        long afterCaffeineGc = usedMemoryBytes();

        com.google.common.cache.Cache<String, byte[]> guava = CacheBuilder.newBuilder()
            .maximumSize(entries)
            .recordStats()
            .build();
        for (int i = 0; i < entries; i++) {
            String k = "k" + i;
            guava.put(k, newValue(valueBytes, k, i));
        }
        long afterGuava = usedMemoryBytes();

        log.info("【内存占用对比】基线占用(字节)={}", base);
        log.info("【内存占用对比】Caffeine：写入后占用(字节)={}, 增量(字节)={}", afterCaffeine, afterCaffeine - base);
        log.info("【内存占用对比】Caffeine：清理+GC后占用(字节)={}, 相对基线增量(字节)={}", afterCaffeineGc, afterCaffeineGc - base);
        log.info("【内存占用对比】Guava   ：写入后占用(字节)={}, 相对上一步增量(字节)={}", afterGuava, afterGuava - afterCaffeineGc);
        log.info("【内存占用对比】结束（注意：JVM 内存估算有噪声，仅用于相对对比）");
    }

    private static String pickKey(Random rnd, int hotKeys, double hotRatio) {
        int k;
        if (rnd.nextDouble() < hotRatio) {
            k = rnd.nextInt(Math.max(1, hotKeys));
        } else {
            k = hotKeys + rnd.nextInt(Math.max(1, hotKeys * 9));
        }
        return "k" + k;
    }

    private static byte[] newValue(int valueBytes, String key, int salt) {
        byte[] v = new byte[Math.max(1, valueBytes)];
        int h = (key == null ? 0 : key.hashCode()) ^ salt;
        v[0] = (byte) h;
        v[v.length - 1] = (byte) (h >>> 8);
        return v;
    }

    private static byte[] guavaGet(com.google.common.cache.Cache<String, byte[]> cache, String key, Callable<byte[]> loader) {
        try {
            return cache.get(key, loader);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static long usedMemoryBytes() {
        forceGc();
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static void forceGc() {
        for (int i = 0; i < 2; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private interface Work {
        Void apply(String key, int i);
    }

    private static final class ThroughputResult {
        private final long ops;
        private final long durationMs;
        private final long opsPerSec;

        private ThroughputResult(long ops, long durationMs, long opsPerSec) {
            this.ops = ops;
            this.durationMs = durationMs;
            this.opsPerSec = opsPerSec;
        }
    }
}
