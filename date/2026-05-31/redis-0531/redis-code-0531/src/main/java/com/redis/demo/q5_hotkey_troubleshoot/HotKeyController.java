package com.redis.demo.q5_hotkey_troubleshoot;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Q5: 热 Key 排查——多级缓存命中率演示.
 *
 * <pre>
 * # 1. 先写一条数据到 Redis
 * curl -X POST 'http://localhost:8080/api/q5/cache/set?key=hot:item&value=99'
 *
 * # 2. 清空本地缓存
 * curl -X POST http://localhost:8080/api/q5/cache/clear
 *
 * # 3. 连续读 3 次——观察 L2 → L1 跃迁
 * curl -X POST 'http://localhost:8080/api/q5/cache/get?key=hot:item'
 * curl -X POST 'http://localhost:8080/api/q5/cache/get?key=hot:item'
 * curl -X POST 'http://localhost:8080/api/q5/cache/get?key=hot:item'
 *
 * # 4. 看命中率
 * curl http://localhost:8080/api/q5/cache/stats
 * </pre>
 */
@RestController
@RequestMapping("/api/q5")
public class HotKeyController {

    @Value("${spring.redis.host:192.168.3.100}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:123456}")
    private String password;

    private Jedis redis;

    // 【重点】Caffeine 本地缓存——微秒级
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofSeconds(5))
            .recordStats()
            .build();

    private final AtomicLong l1Hits = new AtomicLong();
    private final AtomicLong l2Hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    @PostConstruct
    public void init() {
        // cluster profile 没有 spring.redis.host，默认值 6379 连不上。
        // 直接试 7000(cluster) → 6379(standalone) → 6380(sentinel slave)
        String host = redisHost;
        int port = redisPort;
        for (int p : new int[]{7000, 6379, 6380}) {
            try (Jedis j = new Jedis(host, p, 2000)) {
                j.auth(password);
                j.ping();
                port = p;
                break;
            } catch (Exception ignored) {}
        }
        redis = new Jedis(host, port, 3000);
        redis.auth(password);
    }

    // ================================================================
    // 多级缓存读取
    // ================================================================

    /**
     * L1(Caffeine) → L2(Redis) → miss
     *
     * 第一次调返回 L2-Redis（Caffeine 未命中，Redis 命中，回填 Caffeine）。
     * 第二次调返回 L1-Caffeine（微秒级命中）。
     */
    @PostMapping("/cache/get")
    public Map<String, Object> get(@RequestParam String key) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("key", key);

        // L1: Caffeine 本地缓存
        String cached = localCache.getIfPresent(key);
        if (cached != null) {
            l1Hits.incrementAndGet();
            r.put("level", "L1-Caffeine");
            r.put("latency", "< 1μs（微秒级——直接内存读取）");
            r.put("value", cached);
            r.put("stats", stats());
            return r;
        }

        // L2: Redis
        try {
            String val = redis.get(key);
            if (val != null) {
                l2Hits.incrementAndGet();
                localCache.put(key, val); // 【重点】回填本地缓存
                r.put("level", "L2-Redis");
                r.put("latency", "~1ms（毫秒级——网络往返）");
                r.put("value", val);
                r.put("note", "已回填 L1——下次请求将命中 Caffeine（<1μs）");
                r.put("stats", stats());
                return r;
            }
        } catch (Exception e) {
            r.put("redisError", e.getMessage());
        }

        misses.incrementAndGet();
        r.put("level", "MISS");
        r.put("latency", "~10ms（需查 DB——缓存击穿！）");
        r.put("stats", stats());
        return r;
    }

    @PostMapping("/cache/set")
    public Map<String, Object> set(@RequestParam String key, @RequestParam String value) {
        redis.set(key, value);
        localCache.put(key, value);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("key", key); r.put("value", value);
        r.put("status", "OK——同时写入 Redis 和 Caffeine");
        return r;
    }

    @PostMapping("/cache/clear")
    public Map<String, Object> clear() {
        localCache.invalidateAll();
        l1Hits.set(0); l2Hits.set(0); misses.set(0);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "Caffeine 已清空——再次请求将全部走 Redis");
        return r;
    }

    @GetMapping("/cache/stats")
    public Map<String, Object> stats() {
        long total = l1Hits.get() + l2Hits.get() + misses.get();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("l1Hits", l1Hits.get());
        s.put("l2Hits", l2Hits.get());
        s.put("misses", misses.get());
        s.put("totalRequests", total);
        s.put("hitRate", total == 0 ? "0%" :
                String.format("%.1f%%", 100.0 * (l1Hits.get() + l2Hits.get()) / total));
        s.put("caffeineSize", localCache.estimatedSize());
        s.put("evictions", localCache.stats().evictionCount());
        s.put("principle",
                "L1(Caffeine 微秒级) + L2(Redis 毫秒级) + L3(DB)。"
                        + "热 Key 通过 L1 卸载 99% 读流量——这就是面试题 Q5 的长效方案。");
        return s;
    }
}
