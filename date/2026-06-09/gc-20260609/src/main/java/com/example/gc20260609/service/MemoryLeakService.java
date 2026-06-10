package com.example.gc20260609.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * <h2>问题根因：SoftReference 缓存撑满老年代 → SerialGC 单线程扫描大量活对象 → STW 毛刺</h2>
 *
 * <h3>💣 这段代码有什么问题？</h3>
 * <p>
 * 开发同学用 <strong>SoftReference</strong> 做了一个本地缓存。<br>
 * 软引用的特点：内存充足时一直存活，内存不足时被 JVM 清除。<br>
 * 看起来很完美？但实际上：缓存填满老年代后，<strong>Full GC（SerialGC 单线程）必须先扫描
 * 所有活对象（1200MB），然后才发现内存不够、清除软引用</strong>。
 * 单线程扫描 1.2GB 活对象的 STW 就是毛刺的来源（~1.5-3 秒）。
 * </p>
 *
 * <h3>🔥 完整的故障链路（循环发生，不会 OOM）</h3>
 * <pre>
 *   1. 每 1 秒往缓存写入 10MB（SoftReference 包装）
 *   2. byte[] 晋升到老年代，SoftReference 保护它们不被 Young GC 回收
 *   3. 缓存达到 120 条（1200MB）→ 老年代 88% 被活对象占据
 *   4. 继续分配触发 Full GC（SerialGC 单线程）
 *   5. Full GC 标记阶段：单线程遍历所有 1200MB 活对象（因为软引用还活着）
 *   6. 标记完成后发现空间不够 → JVM 清除所有 SoftReference → 回收 1200MB
 *   7. 步骤 5 的单线程遍历耗时就是 STW 毛刺（~1.5-3 秒）
 *   8. 缓存自动失效（ref.get() == null）→ 重新填充 → 循环...
 * </pre>
 *
 * <h3>🔑 为什么 SoftReference 会产生毛刺，而 clear() 不会？</h3>
 * <pre>
 *   clear() 方式：缓存清空 → 对象变垃圾 → Full GC 快速跳过垃圾 → STW 短（无毛刺）
 *   SoftRef 方式：缓存不主动清 → 对象都是活的 → Full GC 必须遍历所有活对象 → STW 长（有毛刺）
 *
 *   Full GC 耗时 ∝ 活对象数量（不是堆大小）
 *   clear() 后活对象少 → GC 快
 *   SoftRef 清除前活对象多 → GC 慢 → 毛刺！
 * </pre>
 *
 * <h3>📊 堆内存布局（-Xms2g -Xmx2g，SerialGC）</h3>
 * <pre>
 *   堆总大小: 2048MB (2GB)
 *   新生代:   ~682MB（Eden ~546MB + Survivor ~68MB×2）
 *   老年代:   ~1365MB
 *
 *   缓存 120 条 × 10MB = 1200MB（SoftReference 保护）→ 占老年代 88%
 *   每 1 秒写入 10MB → 约 120 秒填满 → SerialGC 单线程扫描 1.2GB → STW 1.5~3s → 循环
 * </pre>
 *
 * <h3>🔧 真实生产环境中的类似场景</h3>
 * <ul>
 *   <li>SoftReference / WeakReference 做本地缓存（Hibernate 二级缓存早期版本）</li>
 *   <li>图片/文件缓存用软引用"让 JVM 自动管理"</li>
 *   <li>大对象长期持有直到 GC 压力才释放</li>
 *   <li>本地缓存没有主动淘汰策略，全靠 GC 回收</li>
 * </ul>
 */
@Service
public class MemoryLeakService {

    private static final Logger log = LoggerFactory.getLogger(MemoryLeakService.class);

    /**
     * 💣 问题代码：用 SoftReference 包装缓存值
     * <p>
     * 软引用让 byte[] 在内存不足前一直存活（晋升到老年代），<br>
     * Full GC 时必须扫描所有存活的软引用对象（1200MB 活对象），<br>
     * 这个扫描过程的 STW 就是接口毛刺的来源。
     * </p>
     * <p>
     * <strong>正确做法：</strong>使用 Caffeine / Guava Cache 的 <strong>主动淘汰</strong>（LRU/LFU），
     * 不要依赖 JVM GC 来管理缓存生命周期：
     * <pre>
     * LoadingCache&lt;String, byte[]&gt; cache = Caffeine.newBuilder()
     *     .maximumSize(120)
     *     .expireAfterWrite(10, TimeUnit.MINUTES)
     *     .build(key -&gt; loadFromDB(key));
     * </pre>
     */
    private static final Map<String, SoftReference<byte[]>> CACHE = new HashMap<>();

    /** 每条缓存数据大小：10MB */
    private static final int ENTRY_SIZE = 10 * 1024 * 1024;

    /**
     * 缓存上限：120 条 × 10MB = 1200MB
     * <p>
     * 老年代约 1365MB，缓存占 1200MB（88%）。<br>
     * 填满后 SerialGC 单线程扫描 1.2GB 活对象 → STW 1.5~3 秒毛刺。<br>
     * 然后 JVM 清除 SoftReference → 回收内存 → 缓存自动失效 → 重新填充。
     * </p>
     */
    private static final int MAX_CACHE_SIZE = 120;

    /** 写入计数器 */
    private int writeCount = 0;

    /**
     * 定时任务：每 1 秒填充缓存
     * <p>
     * 与之前 clear() 方式的核心区别：<br>
     * - <strong>不主动清除缓存</strong>，让 JVM 在 Full GC 时自己处理 SoftReference<br>
     * - Full GC 标记阶段必须遍历所有 1200MB 活对象 → 这就是 STW 毛刺<br>
     * - 标记完成后 JVM 清除软引用 → ref.get() 返回 null → 槽位可重用
     * </p>
     */
    @Scheduled(fixedRate = 1000)
    public void cacheData() {
        // 统计存活和已被 JVM 清除的条目
        int liveCount = 0;
        int clearedCount = 0;
        String refillKey = null;

        for (Map.Entry<String, SoftReference<byte[]>> entry : CACHE.entrySet()) {
            if (entry.getValue().get() != null) {
                liveCount++;
            } else {
                clearedCount++;
                if (refillKey == null) {
                    refillKey = entry.getKey(); // 记录第一个被清除的槽位
                }
            }
        }

        if (liveCount >= MAX_CACHE_SIZE) {
            // 缓存已满且全部存活 → 等待 JVM 在 Full GC 时清除 SoftReference
            // 此时老年代有 1200MB 活对象，SerialGC 单线程全部扫描 → STW 毛刺
            return;
        }

        // 有空位（JVM 已清除部分软引用，或缓存尚未填满）→ 填充
        writeCount++;
        String key;
        if (refillKey != null) {
            key = refillKey; // 重用被清除的槽位
        } else {
            key = "cache-key-" + writeCount; // 新槽位
        }

        // 分配 10MB，用 SoftReference 包装
        byte[] value = new byte[ENTRY_SIZE];
        for (int i = 0; i < 1000; i++) {
            value[i] = (byte) (writeCount % 256);
        }
        CACHE.put(key, new SoftReference<>(value));

        // 每 10 条打印一次状态
        if (writeCount % 10 == 0) {
            long cacheSizeMB = (long) liveCount * ENTRY_SIZE / (1024 * 1024);
            log.info("⚠️ [MemoryLeakService] 缓存状态 | 存活={}条({}MB) | JVM已清除={}条 | 堆={}/{}MB",
                    liveCount, cacheSizeMB, clearedCount,
                    getUsedHeapMB(), getMaxHeapMB());
        }
    }

    private long getUsedHeapMB() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
    }

    private long getMaxHeapMB() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / (1024 * 1024);
    }
}
