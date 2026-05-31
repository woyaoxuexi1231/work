package com.redis.demo.controller;

import com.redis.demo.cluster.HotKeyDetector;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Q5: 热 Key/大 Key 排查与多级缓存 —— HTTP 接口.
 *
 * <h3>对比实验路径</h3>
 * <pre>
 *   # 1. 查看慢查询日志
 *   curl 'http://localhost:8080/api/cluster/troubleshoot/slowlog?topN=5'
 *
 *   # 2. 大 Key 检测
 *   curl http://localhost:8080/api/cluster/troubleshoot/bigkeys
 *
 *   # 3. 热 Key 检测
 *   curl http://localhost:8080/api/cluster/troubleshoot/hotkeys
 *
 *   # 4. 多级缓存读取——连续调用 3 次观察命中率变化
 *   curl -X POST 'http://localhost:8080/api/cluster/troubleshoot/cache/get?key=seckill:phone-123'
 *   curl -X POST 'http://localhost:8080/api/cluster/troubleshoot/cache/get?key=seckill:phone-123'
 *   curl -X POST 'http://localhost:8080/api/cluster/troubleshoot/cache/get?key=seckill:phone-123'
 *
 *   # 5. 写入缓存
 *   curl -X POST 'http://localhost:8080/api/cluster/troubleshoot/cache/set?key=hot:item&value=99'
 *
 *   # 6. 查看缓存命中率
 *   curl http://localhost:8080/api/cluster/troubleshoot/cache/stats
 *
 *   # 7. 清空本地缓存——观察命中率归零
 *   curl -X POST http://localhost:8080/api/cluster/troubleshoot/cache/clear
 *
 *   # 8. 缓存预热
 *   curl -X POST http://localhost:8080/api/cluster/troubleshoot/cache/warmup \
 *     -H 'Content-Type: application/json' \
 *     -d '["seckill:phone-123","news:breaking:2024","stock:btc:price"]'
 * </pre>
 */
@RestController
@RequestMapping("/api/cluster/troubleshoot")
public class ClusterTroubleshootController {

    private final HotKeyDetector detector;

    public ClusterTroubleshootController(HotKeyDetector detector) {
        this.detector = detector;
    }

    /** 慢查询日志. */
    @GetMapping("/slowlog")
    public Map<String, Object> slowlog(@RequestParam(defaultValue = "5") int topN) {
        return detector.getSlowLog(topN);
    }

    /** 大 Key 检测. */
    @GetMapping("/bigkeys")
    public Map<String, Object> bigKeys() {
        return detector.detectBigKeys();
    }

    /** 热 Key 检测. */
    @GetMapping("/hotkeys")
    public Map<String, Object> hotKeys() {
        return detector.detectHotKeys();
    }

    /**
     * 【关键实验】多级缓存读取.
     *
     * 连续调用 3 次同一个 key，观察 cacheLevel 的变化：
     *   第一次: L2-Redis（Caffeine 未命中 → Redis 命中 → 回填 Caffeine）
     *   第二次: L1-Caffeine（Caffeine 命中，微秒级）
     *   第三次: L1-Caffeine（仍然命中）
     */
    @PostMapping("/cache/get")
    public Map<String, Object> cacheGet(@RequestParam String key) {
        return detector.multiLevelGet(key);
    }

    /** 写入缓存. */
    @PostMapping("/cache/set")
    public Map<String, Object> cacheSet(@RequestParam String key,
                                         @RequestParam String value) {
        return detector.multiLevelSet(key, value);
    }

    /** 缓存命中率. */
    @GetMapping("/cache/stats")
    public Map<String, Object> cacheStats() {
        return detector.getCacheStats();
    }

    /** 清空本地缓存. */
    @PostMapping("/cache/clear")
    public Map<String, Object> cacheClear() {
        return detector.clearLocalCache();
    }

    /** 缓存预热. */
    @PostMapping("/cache/warmup")
    public Map<String, Object> cacheWarmup(@RequestBody List<String> keys) {
        return detector.warmUp(keys);
    }
}
