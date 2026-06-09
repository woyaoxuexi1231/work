package com.example.gc20260609.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <h2>问题根因：本地缓存定期重建 → 老年代堆积垃圾 → Full GC STW 毛刺</h2>
 *
 * <h3>💣 这段代码有什么问题？</h3>
 * <p>
 * 模拟一个常见的生产环境场景：<br>
 * 开发同学加了一个<strong>本地缓存</strong>（HashMap），并做了容量控制（60 条后清空重建）。<br>
 * 虽然不会 OOM，但每次清空后，旧数据晋升到了老年代变成了垃圾，<br>
 * 只有 Full GC 才能回收 → <strong>Full GC STW 导致接口偶发性毛刺</strong>。
 * </p>
 *
 * <h3>🔥 完整的故障链路（循环发生，不会 OOM）</h3>
 * <pre>
 *   1. 每 1 秒往缓存里写入 10MB 数据
 *   2. 这些 byte[] 长期存活，经过多次 Young GC 后晋升到老年代
 *   3. 缓存达到 60 条（600MB）→ 触发 clear() 清空重建
 *   4. 被清空的 byte[] 在老年代变成了“垃圾”（只有 Full GC 能回收老年代）
 *   5. 新缓存数据继续填充 → 老年代再次逼近上限
 *   6. JVM 触发 Full GC（STW 2~5s）→ 回收上一轮清空的老数据
 *   7. Full GC 期间所有线程暂停 → 接口出现毛刺
 *   8. Full GC 结束 → 恢复正常 → 缓存继续增长 → 循环...
 * </pre>
 *
 * <h3>📊 堆内存布局（-Xms1g -Xmx1g，ParallelGC）</h3>
 * <pre>
 *   堆总大小: 1024MB (1GB)
 *   新生代:   ~340MB（Eden ~272MB + Survivor ~34MB×2）
 *   老年代:   ~684MB
 *
 *   缓存上限 60 条 × 10MB = 600MB → 占老年代 88%
 *   每 1 秒写入 10MB → 约 60 秒填满 → 清空 → Full GC 回收 → 循环
 * </pre>
 *
 * <h3>🔑 为什么不会 OOM？</h3>
 * <p>
 * 因为缓存会定期 clear()，旧数据变成垃圾后 Full GC 能回收。<br>
 * 但 Full GC 的 STW（Stop The World）仍然会造成 2~5 秒的停顿。<br>
 * 这就是面试官说的"<strong>偶发性慢</strong>"——服务没挂，但偶尔会卡一下。
 * </p>
 *
 * <h3>🔧 真实生产环境中的类似场景</h3>
 * <ul>
 *   <li>定时刷新本地缓存（每 N 分钟全量 rebuild）</li>
 *   <li>批量数据处理完丢弃，下一批又来了</li>
 *   <li>报表系统定期加载大量数据做聚合计算</li>
 *   <li>配置中心定期拉取大量配置到本地</li>
 * </ul>
 */
@Service
public class MemoryLeakService {

    private static final Logger log = LoggerFactory.getLogger(MemoryLeakService.class);

    /**
     * 💣 问题代码：虽然有上限，但缓存满了直接 clear() 重建，
     * 导致大量老年代对象变成垃圾，频繁触发 Full GC。
     * <p>
     * 正确做法：使用 Caffeine / Guava Cache 的 <strong>逐条淘汰</strong>（LRU/LFU），
     * 而不是全量清空重建：
     * <pre>
     * LoadingCache&lt;String, byte[]&gt; cache = Caffeine.newBuilder()
     *     .maximumSize(60)
     *     .expireAfterWrite(10, TimeUnit.MINUTES)
     *     .build(key -&gt; loadFromDB(key));
     * </pre>
     */
    private static final Map<String, byte[]> CACHE = new HashMap<>();

    /** 缓存写入计数器 */
    private final AtomicInteger writeCount = new AtomicInteger(0);

    /** 每条缓存数据大小：10MB */
    private static final int ENTRY_SIZE = 10 * 1024 * 1024;

    /**
     * 缓存上限：60 条 × 10MB = 600MB
     * <p>
     * 老年代约 684MB，缓存占 600MB（88%）→ 接近填满。<br>
     * 达到上限后清空重建，旧数据变成老年代垃圾 → 触发 Full GC。
     * </p>
     */
    private static final int MAX_CACHE_SIZE = 60;

    /**
     * 定时任务：每 1 秒往缓存里写入 10MB 数据
     * <p>
     * 当缓存达到 MAX_CACHE_SIZE 时，清空重建。<br>
     * 被清空的旧数据已经在老年代，变成垃圾后等待 Full GC 回收。
     * </p>
     */
    @Scheduled(fixedRate = 1000)
    public void cacheData() {
        // 缓存达到上限 → 清空重建（模拟缓存定期 refresh）
        if (CACHE.size() >= MAX_CACHE_SIZE) {
            int oldSize = CACHE.size();
            CACHE.clear();
            log.info("⚠️ [MemoryLeakService] 缓存重建 | 清空 {} 条旧数据 | 旧数据已进入老年代变成垃圾",
                    oldSize);
        }

        int count = writeCount.incrementAndGet();
        String key = "cache-key-" + count;

        // 每次分配 10MB 的 byte[]，模拟一条较大的缓存数据
        byte[] value = new byte[ENTRY_SIZE];
        // 填充一些数据，避免 JVM 优化掉分配
        for (int i = 0; i < 100; i++) {
            value[i] = (byte) (count % 256);
        }

        CACHE.put(key, value);

        // 每写入 10 条打印一次状态（避免刷屏）
        if (count % 10 == 0) {
            long cacheSizeMB = (long) CACHE.size() * ENTRY_SIZE / (1024 * 1024);
            log.info("⚠️ [MemoryLeakService] 缓存增长中 | 条目数={} | 缓存大小={}MB | 堆内存使用={}/{}MB",
                    CACHE.size(),
                    cacheSizeMB,
                    getUsedHeapMB(),
                    getMaxHeapMB());
        }
    }

    private long getUsedHeapMB() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
    }

    private long getMaxHeapMB() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / (1024 * 1024);
    }
}
