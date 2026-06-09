package com.example.java20260608.controller;

import com.example.java20260608.cache.CaffeineNativeCache;
import com.example.java20260608.cache.GuavaNativeCache;
import com.example.java20260608.service.SlowValueService;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.cache.Cache;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/native/compare")
public class NativeCacheController {

    private final CaffeineNativeCache caffeineNativeCache;
    private final GuavaNativeCache guavaNativeCache;
    private final SlowValueService slowValueService;

    public NativeCacheController(
        CaffeineNativeCache caffeineNativeCache,
        GuavaNativeCache guavaNativeCache,
        SlowValueService slowValueService
    ) {
        this.caffeineNativeCache = caffeineNativeCache;
        this.guavaNativeCache = guavaNativeCache;
        this.slowValueService = slowValueService;
    }

    /**
     * 对比点：expireAfterWrite（写入后过期）
     * - 写入后开始计时
     * - 即使中间频繁访问，也不会延长 TTL
     */
    @GetMapping("/expireAfterWrite")
    public Map<String, Object> expireAfterWrite(
        @RequestParam("key") String key,
        @RequestParam(value = "sleepMs", defaultValue = "35000") long sleepMs
    ) {
        com.github.benmanes.caffeine.cache.Cache<String, String> caffeine = caffeineNativeCache.cache();
        Cache<String, String> guava = guavaNativeCache.cache();

        String caffeineV1 = caffeine.get(key, k -> slowValueService.compute("caffeine-eaw:" + k));
        String guavaV1 = guavaGet(guava, key, () -> slowValueService.compute("guava-eaw:" + key));

        sleep(sleepMs);

        String caffeineV2 = caffeine.get(key, k -> slowValueService.compute("caffeine-eaw:" + k));
        String guavaV2 = guavaGet(guava, key, () -> slowValueService.compute("guava-eaw:" + key));

        Map<String, Object> caffeineResult = new LinkedHashMap<String, Object>();
        caffeineResult.put("第一次结果", caffeineV1);
        caffeineResult.put("第二次结果", caffeineV2);
        caffeineResult.put("是否相同", caffeineV1 != null && caffeineV1.equals(caffeineV2));
        caffeineResult.put("统计", caffeineStats("Caffeine(写后过期)", caffeine.stats()));

        Map<String, Object> guavaResult = new LinkedHashMap<String, Object>();
        guavaResult.put("第一次结果", guavaV1);
        guavaResult.put("第二次结果", guavaV2);
        guavaResult.put("是否相同", guavaV1 != null && guavaV1.equals(guavaV2));
        guavaResult.put("统计", guavaStats("Guava(写后过期)", guava.stats()));

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("对比项", "写后过期 expireAfterWrite(30s)");
        m.put("key", key);
        m.put("休眠毫秒", sleepMs);
        m.put("Caffeine", caffeineResult);
        m.put("Guava", guavaResult);
        return m;
    }

    /**
     * 对比点：expireAfterAccess（访问后过期）
     * - 每次访问都会刷新过期时间（更适合“热点数据”场景）
     * - 这里用 getIfPresent 作为“触碰（touch）”，模拟读操作延长 TTL
     */
    @GetMapping("/expireAfterAccess")
    public Map<String, Object> expireAfterAccess(
        @RequestParam("key") String key,
        @RequestParam(value = "touchCount", defaultValue = "3") int touchCount,
        @RequestParam(value = "touchIntervalMs", defaultValue = "8000") long touchIntervalMs,
        @RequestParam(value = "afterTouchSleepMs", defaultValue = "35000") long afterTouchSleepMs
    ) {
        com.github.benmanes.caffeine.cache.Cache<String, String> caffeine = caffeineNativeCache.expireAfterAccessCache();
        Cache<String, String> guava = guavaNativeCache.expireAfterAccessCache();

        String caffeineV1 = caffeine.get(key, k -> slowValueService.compute("caffeine-eaa:" + k));
        String guavaV1 = guavaGet(guava, key, () -> slowValueService.compute("guava-eaa:" + key));

        for (int i = 0; i < touchCount; i++) {
            sleep(touchIntervalMs);
            caffeine.getIfPresent(key);
            guava.getIfPresent(key);
        }
        sleep(afterTouchSleepMs);

        String caffeineV2 = caffeine.get(key, k -> slowValueService.compute("caffeine-eaa:" + k));
        String guavaV2 = guavaGet(guava, key, () -> slowValueService.compute("guava-eaa:" + key));

        Map<String, Object> caffeineResult = new LinkedHashMap<String, Object>();
        caffeineResult.put("第一次结果", caffeineV1);
        caffeineResult.put("第二次结果", caffeineV2);
        caffeineResult.put("是否相同", caffeineV1 != null && caffeineV1.equals(caffeineV2));
        caffeineResult.put("统计", caffeineStats("Caffeine(访问后过期)", caffeine.stats()));

        Map<String, Object> guavaResult = new LinkedHashMap<String, Object>();
        guavaResult.put("第一次结果", guavaV1);
        guavaResult.put("第二次结果", guavaV2);
        guavaResult.put("是否相同", guavaV1 != null && guavaV1.equals(guavaV2));
        guavaResult.put("统计", guavaStats("Guava(访问后过期)", guava.stats()));

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("对比项", "访问后过期 expireAfterAccess(30s)");
        m.put("key", key);
        m.put("触碰次数", touchCount);
        m.put("触碰间隔毫秒", touchIntervalMs);
        m.put("触碰后休眠毫秒", afterTouchSleepMs);
        m.put("Caffeine", caffeineResult);
        m.put("Guava", guavaResult);
        return m;
    }

    /**
     * 对比点：maximumSize（基于容量的淘汰）
     * - maximumSize 很小（这里=3），写入更多 key 后会触发淘汰
     * - Caffeine 的淘汰策略更先进（W-TinyLFU），但这里主要看“会淘汰”的现象
     */
    @GetMapping("/maxSize")
    public Map<String, Object> maxSize(@RequestParam(value = "count", defaultValue = "10") int count) {
        com.github.benmanes.caffeine.cache.Cache<String, String> caffeine = caffeineNativeCache.maxSize3Cache();
        Cache<String, String> guava = guavaNativeCache.maxSize3Cache();

        caffeine.invalidateAll();
        guava.invalidateAll();

        for (int i = 0; i < count; i++) {
            String k = "k" + i;
            caffeine.put(k, slowValueService.compute("caffeine-maxsize:" + k));
            guava.put(k, slowValueService.compute("guava-maxsize:" + k));
        }

        Map<String, Object> caffeineResult = new LinkedHashMap<String, Object>();
        caffeineResult.put("估算当前大小", caffeine.estimatedSize());
        caffeineResult.put("k0是否存在", caffeine.getIfPresent("k0") != null);
        caffeineResult.put("统计", caffeineStats("Caffeine(maxSize=3)", caffeine.stats()));

        Map<String, Object> guavaResult = new LinkedHashMap<String, Object>();
        guavaResult.put("当前大小", guava.size());
        guavaResult.put("k0是否存在", guava.getIfPresent("k0") != null);
        guavaResult.put("统计", guavaStats("Guava(maxSize=3)", guava.stats()));

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("对比项", "容量淘汰 maximumSize(3)");
        m.put("写入key数量", count);
        m.put("Caffeine", caffeineResult);
        m.put("Guava", guavaResult);
        return m;
    }

    private static Map<String, Object> caffeineStats(String cacheName, CacheStats stats) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", cacheName);
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    private static Map<String, Object> guavaStats(String cacheName, com.google.common.cache.CacheStats stats) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", cacheName);
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    private static String guavaGet(Cache<String, String> cache, String key, java.util.concurrent.Callable<String> loader) {
        try {
            return cache.get(key, loader);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
