package com.redis.demo.cluster;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Q5: 热 Key / 大 Key 排查与多级缓存.
 *
 * <h3>排查链路（面试标准答案）</h3>
 * <ol>
 *   <li>看监控：Grafana 确认 CPU/QPS/网络流量是否单节点异常</li>
 *   <li>查慢日志：SLOWLOG GET 100 定位慢命令</li>
 *   <li>扫 Big/Hot Key：redis-cli --bigkeys / --hotkeys</li>
 *   <li>追业务代码：定位到具体调用方</li>
 *   <li>应急止血 + 长效方案</li>
 * </ol>
 *
 * <h3>对应面试题 Q5</h3>
 * <ul>
 *   <li>CPU 90%+ 排查思路</li>
 *   <li>热 Key 应急（读写分离、本地缓存、key 拆分）</li>
 *   <li>多级缓存命中率</li>
 * </ul>
 */
@Service
public class HotKeyDetector {

    private static final Logger log = LoggerFactory.getLogger(HotKeyDetector.class);

    //【重点】模拟慢查询日志
    private final List<Map<String, Object>> slowLog = new ArrayList<>();

    //【重点】热 Key 计数器——模拟 redis-cli --hotkeys 的采集逻辑
    private final ConcurrentHashMap<String, AtomicLong> keyAccessCount = new ConcurrentHashMap<>();

    // 预置的大 Key 数据（模拟 redis-cli --bigkeys 扫描结果）
    private final Map<String, Long> bigKeys = new LinkedHashMap<>();

    //【重点】Caffeine 本地缓存 —— Q5 多级缓存的核心
    private final Cache<String, String> localCache;

    private final StringRedisTemplate redisTemplate;
    private final SlotCalculator slotCalculator;

    // 统计
    private long localHits = 0, redisHits = 0, totalRequests = 0;

    public HotKeyDetector(StringRedisTemplate redisTemplate,
                          SlotCalculator slotCalculator) {
        this.redisTemplate = redisTemplate;
        this.slotCalculator = slotCalculator;

        //【重点】Caffeine 配置：最大 1000 条、写入后 5 秒过期、开启统计
        this.localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(5))
                .recordStats()
                .build();
    }

    @PostConstruct
    public void init() {
        // 预置一些慢查询日志（模拟 SLOWLOG GET 的结果）
        slowLog.add(slowLogEntry(1, 152000L, "KEYS user:session:*",
                "【禁止】KEYS * 会遍历整个键空间，阻塞主线程"));
        slowLog.add(slowLogEntry(2, 89000L, "HGETALL cart:10000001",
                "【大Key】cart:10000001 包含 50 万个 field，返回数据量 15MB"));
        slowLog.add(slowLogEntry(3, 45000L, "SORT comments:hot BY score DESC LIMIT 0 100",
                "【慢命令】SORT 复杂度 O(N+M*log(M))，数据量大时很慢"));

        // 预置大 Key 列表
        bigKeys.put("cart:10000001", 15 * 1024 * 1024L); // 15MB
        bigKeys.put("user:session:dump", 8 * 1024 * 1024L); // 8MB
        bigKeys.put("comments:hot", 22 * 1024 * 1024L);   // 22MB
        bigKeys.put("analytics:2024", 500 * 1024 * 1024L); // 500MB ←【重点】迁移噩梦

        log.info("热 Key 检测器初始化完成，预置 {} 条慢日志，{} 个大 Key",
                slowLog.size(), bigKeys.size());
    }

    // ========================================================================
    // 慢查询分析
    // ========================================================================

    /**
     * 【重点】模拟 SLOWLOG GET —— Redis 慢查询日志分析.
     *
     * Redis 慢查询日志记录执行时间超过 slowlog-log-slower-than 的命令。
     * 每次触发慢日志，说明该命令在阻塞 Redis 主线程。
     */
    public Map<String, Object> getSlowLog(int topN) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "SLOWLOG GET " + topN + "（模拟）");

        // 真实 Redis 配置建议
        result.put("configAdvice", new LinkedHashMap<String, String>() {{
            put("slowlog-log-slower-than", "10000 (10ms)，超过此阈值即记录");
            put("slowlog-max-len", "128，慢日志最大条数");
            put("运维建议", "定期 SLOWLOG RESET 清空历史，防止内存占用");
        }});

        List<Map<String, Object>> top = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, slowLog.size()); i++) {
            top.add(slowLog.get(i));
        }
        result.put("entries", top);

        // 分析
        List<String> analysis = new ArrayList<>();
        for (Map<String, Object> entry : top) {
            String cmd = (String) entry.get("command");
            String advice = (String) entry.get("advice");
            analysis.add(cmd + " → " + advice);
        }
        result.put("analysis", analysis);

        return result;
    }

    /**
     * 【重点】大 Key 检测——模拟 redis-cli --bigkeys.
     *
     * 大 Key 危害：
     * 1. 读取阻塞：GET/HGETALL 大 key 时主线程被占用
     * 2. 迁移噩梦：MIGRATE 500MB key 直接导致主线程阻塞（Q8 的痛点）
     * 3. 内存碎片：频繁修改大 key 导致大量内存碎片
     * 4. 带宽打满：主从同步时大 key 传输占用大量带宽
     */
    public Map<String, Object> detectBigKeys() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "redis-cli --bigkeys 扫描结果（模拟）");

        List<Map<String, Object>> keys = new ArrayList<>();
        for (Map.Entry<String, Long> entry : bigKeys.entrySet()) {
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("key", entry.getKey());
            k.put("size", formatBytes(entry.getValue()));
            k.put("sizeBytes", entry.getValue());

            //【重点】判断是否影响迁移
            if (entry.getValue() > 100 * 1024 * 1024) {
                k.put("risk", "CRITICAL——MIGRATE 时会阻塞主线程，迁移前必须拆分");
            } else if (entry.getValue() > 10 * 1024 * 1024) {
                k.put("risk", "HIGH——可能触发慢查询，建议拆分");
            } else {
                k.put("risk", "MEDIUM");
            }
            keys.add(k);
        }
        result.put("bigKeys", keys);
        result.put("totalCount", keys.size());

        // 面试时的重要建议
        result.put("recommendations", new String[]{
                "1. 日常巡检：crontab 定时执行 redis-cli --bigkeys，输出到监控平台",
                "2. 大 Key 拆分：按用户 ID 尾号/日期分片，如 cart:{userId % 100}",
                "3. 迁移前强制检查：reshard 前必须扫描大 Key，超 100MB 禁止迁移",
                "4. 代码规范：禁止 HGETALL 无限增长的 hash；用 HSCAN 替代 KEYS"
        });

        return result;
    }

    /**
     * 【重点】热 Key 识别——模拟 redis-cli --hotkeys.
     *
     * 热 Key 特征：
     * - 某个 key 的 QPS 远超其他 key
     * - 通常出现在秒杀、热点新闻、明星发微博等场景
     * - 单节点 CPU 被该 key 占满
     */
    public Map<String, Object> detectHotKeys() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "redis-cli --hotkeys 采集结果（模拟）");

        // 模拟采集数据
        Map<String, Long> hotKeyData = new LinkedHashMap<>();
        hotKeyData.put("seckill:phone-123", 5_000_000L);
        hotKeyData.put("news:breaking:2024", 2_300_000L);
        hotKeyData.put("user:celebrity:888", 1_200_000L);
        hotKeyData.put("stock:btc:price", 800_000L);
        hotKeyData.put("normal:key:001", 150L);

        long totalHits = hotKeyData.values().stream().mapToLong(Long::longValue).sum();

        List<Map<String, Object>> hotKeys = new ArrayList<>();
        for (Map.Entry<String, Long> entry : hotKeyData.entrySet()) {
            Map<String, Object> k = new LinkedHashMap<>();
            k.put("key", entry.getKey());
            k.put("hits", entry.getValue());
            k.put("percentage", String.format("%.2f%%",
                    100.0 * entry.getValue() / totalHits));
            k.put("slot", slotCalculator.slotForKey(entry.getKey()));
            if (entry.getValue() > 1_000_000) {
                k.put("level", "CRITICAL——秒杀级热 Key！");
            } else if (entry.getValue() > 100_000) {
                k.put("level", "WARNING——高流量热 Key");
            } else {
                k.put("level", "NORMAL");
            }
            hotKeys.add(k);
        }
        result.put("hotKeys", hotKeys);
        result.put("totalHits", totalHits);

        // 面试必答的应急方案
        result.put("emergency", new String[]{
                "应急-读：增加 Slave 节点分担读流量，或上本地缓存 Caffeine",
                "应急-写：写无法简单分担，需业务降级或消息队列削峰",
                "长效：key 拆分（如按用户尾号 100 份）、前端限流",
                "禁忌：不能直接 DEL 热 Key——会导致缓存击穿，DB 瞬间被打爆"
        });

        // 监控告警建议
        result.put("monitoring", new String[]{
                "Prometheus 采集每个节点的 INFO commandstats",
                "按 slot 维度统计 QPS 分布",
                "slot QPS 超过均值 3 倍 → 钉钉/企微告警",
                "配合 Grafana 热力图直观展示集群热点"
        });

        return result;
    }

    // ========================================================================
    // 多级缓存（Caffeine + Redis）
    // ========================================================================

    /**
     * 【重点】多级缓存读取——Caffeine(本地) → Redis(分布式) → DB(兜底).
     *
     * 这让你亲眼看到缓存命中率的实时变化。
     *
     * 调用流程：
     *   1. 先查 Caffeine 本地缓存（微秒级）
     *   2. 未命中 → 查 Redis（毫秒级）
     *   3. Redis 命中 → 回填 Caffeine
     *   4. 都未命中 → 返回 miss（实际应查 DB）
     */
    public Map<String, Object> multiLevelGet(String key) {
        totalRequests++;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);

        // Level 1: Caffeine 本地缓存
        String cached = localCache.getIfPresent(key);
        if (cached != null) {
            localHits++;
            result.put("cacheLevel", "L1-Caffeine");
            result.put("hit", true);
            result.put("latency", "< 1μs（微秒级）");
            result.put("value", cached);
            result.put("stats", buildStats());
            return result;
        }

        // Level 2: Redis 分布式缓存
        try {
            String redisValue = redisTemplate.opsForValue().get(key);
            if (redisValue != null) {
                redisHits++;
                //【重点】回填本地缓存——下次请求直接命中 Caffeine
                localCache.put(key, redisValue);
                result.put("cacheLevel", "L2-Redis");
                result.put("hit", true);
                result.put("latency", "~1ms（毫秒级）");
                result.put("value", redisValue);
                result.put("note", "已回填 L1 缓存，下次请求将命中 Caffeine");
                result.put("stats", buildStats());
                return result;
            }
        } catch (Exception e) {
            result.put("redisError", e.getMessage());
        }

        // Level 3: 全部 miss
        result.put("cacheLevel", "MISS");
        result.put("hit", false);
        result.put("latency", "~10ms（需要查 DB，此处不演示）");
        result.put("value", null);
        result.put("note", "缓存击穿！应查 DB 并回填 Redis + Caffeine");
        result.put("stats", buildStats());
        return result;
    }

    /**
     * 写入缓存——同时更新 Redis 和 Caffeine.
     */
    public Map<String, Object> multiLevelSet(String key, String value) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 写 Redis
            redisTemplate.opsForValue().set(key, value);
            result.put("redis", "OK");
        } catch (Exception e) {
            result.put("redis", "FAILED: " + e.getMessage());
        }

        // 写 Caffeine
        localCache.put(key, value);
        result.put("caffeine", "OK");

        //【重点】缓存一致性说明
        result.put("consistency", "Caffeine TTL=5s，允许 5 秒内的最终一致。"
                + "若需强一致，写操作后应主动失效本地缓存（Cache.invalidate(key)）");

        result.put("key", key);
        result.put("value", value);
        return result;
    }

    /**
     * 批量预热——模拟大促前的缓存预热.
     */
    public Map<String, Object> warmUp(List<String> keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        int loaded = 0, failed = 0;

        for (String key : keys) {
            try {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    localCache.put(key, value);
                    loaded++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }

        result.put("total", keys.size());
        result.put("loaded", loaded);
        result.put("failed", failed);
        result.put("note", "缓存预热完成——Caffeine 中已加载 " + loaded
                + " 个 key，业务高峰时直接命中 L1 缓存");

        CacheStats stats = localCache.stats();
        result.put("cacheSize", localCache.estimatedSize());
        result.put("hitRate", String.format("%.2f%%", stats.hitRate() * 100));

        return result;
    }

    /**
     * 缓存命中率统计.
     */
    public Map<String, Object> getCacheStats() {
        return buildStats();
    }

    /** 清空本地缓存（手动模拟缓存失效）. */
    public Map<String, Object> clearLocalCache() {
        localCache.invalidateAll();
        localHits = 0;
        redisHits = 0;
        totalRequests = 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "cleared");
        result.put("note", "本地缓存已清空——再次请求将全部走 Redis");
        return result;
    }

    // ---- helpers -----------------------------------------------------------

    private Map<String, Object> buildStats() {
        CacheStats stats = localCache.stats();
        double hitRate = totalRequests > 0
                ? (double) (localHits + redisHits) / totalRequests
                : 0;

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalRequests", totalRequests);
        s.put("l1Hits", localHits);
        s.put("l2Hits", redisHits);
        s.put("misses", totalRequests - localHits - redisHits);
        s.put("hitRate", String.format("%.2f%%", hitRate * 100));
        s.put("caffeineSize", localCache.estimatedSize());
        s.put("caffeineHitRate", String.format("%.2f%%", stats.hitRate() * 100));
        s.put("caffeineEvictions", stats.evictionCount());
        return s;
    }

    private Map<String, Object> slowLogEntry(int id, long durationUs,
                                             String command, String advice) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("duration", durationUs + "μs (" + (durationUs / 1000) + "ms)");
        entry.put("command", command);
        entry.put("advice", advice);
        entry.put("timestamp", new Date().toString());
        return entry;
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024 * 1024)        return String.format("%.1f MB", bytes / (1024.0 * 1024));
        if (bytes >= 1024)               return String.format("%.1f KB", bytes / 1024.0);
        return bytes + " B";
    }
}
