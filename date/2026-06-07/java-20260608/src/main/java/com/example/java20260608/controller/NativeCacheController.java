package com.example.java20260608.controller;

import com.example.java20260608.cache.CaffeineNativeCache;
import com.example.java20260608.cache.GuavaNativeCache;
import com.example.java20260608.service.SlowValueService;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.cache.Cache;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/native")
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
     * 最基础的原生用法：Cache.get(key, mappingFunction)
     * - 第一次访问：miss -> 调用 mappingFunction 计算并写入缓存
     * - 第二次访问：hit -> 直接返回缓存值
     */
    @GetMapping("/caffeine/load")
    public Map<String, Object> caffeineLoad(@RequestParam("key") String key) {
        String value = caffeineNativeCache.cache().get(key, k -> slowValueService.compute("caffeine:" + k));
        return response("Caffeine", key, value);
    }

    /**
     * 原生 put：手动写入缓存
     */
    @GetMapping("/caffeine/put")
    public Map<String, Object> caffeinePut(@RequestParam("key") String key, @RequestParam("value") String value) {
        caffeineNativeCache.cache().put(key, value);
        return response("Caffeine", key, value);
    }

    /**
     * 原生 invalidate：手动失效指定 key
     */
    @DeleteMapping("/caffeine/invalidate")
    public Map<String, Object> caffeineInvalidate(@RequestParam("key") String key) {
        caffeineNativeCache.cache().invalidate(key);
        return response("Caffeine", key, null);
    }

    /**
     * Caffeine 统计信息（需要 recordStats() 才会有）
     */
    @GetMapping("/caffeine/stats")
    public Map<String, Object> caffeineStats() {
        CacheStats stats = caffeineNativeCache.cache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine(写后过期)");
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    /**
     * 对比点：expireAfterWrite（写入后过期）
     * - 写入后开始计时
     * - 即使中间频繁访问，也不会延长 TTL
     */
    @GetMapping("/caffeine/scenario/expireAfterWrite")
    public Map<String, Object> caffeineExpireAfterWrite(
        @RequestParam("key") String key,
        @RequestParam(value = "sleepMs", defaultValue = "35000") long sleepMs
    ) {
        String v1 = caffeineNativeCache.cache().get(key, k -> slowValueService.compute("caffeine-eaw:" + k));
        sleep(sleepMs);
        String v2 = caffeineNativeCache.cache().get(key, k -> slowValueService.compute("caffeine-eaw:" + k));

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine");
        m.put("场景", "写后过期 expireAfterWrite(30s)");
        m.put("key", key);
        m.put("第一次结果", v1);
        m.put("第二次结果", v2);
        m.put("是否相同", v1 != null && v1.equals(v2));
        m.put("休眠毫秒", sleepMs);
        m.put("统计", caffeineStats());
        return m;
    }

    /**
     * 对比点：expireAfterAccess（访问后过期）
     * - 每次访问都会刷新过期时间（更适合“热点数据”场景）
     * - 这里用 getIfPresent 作为“触碰（touch）”，模拟读操作延长 TTL
     */
    @GetMapping("/caffeine/scenario/expireAfterAccess")
    public Map<String, Object> caffeineExpireAfterAccess(
        @RequestParam("key") String key,
        @RequestParam(value = "touchCount", defaultValue = "3") int touchCount,
        @RequestParam(value = "touchIntervalMs", defaultValue = "8000") long touchIntervalMs,
        @RequestParam(value = "afterTouchSleepMs", defaultValue = "35000") long afterTouchSleepMs
    ) {
        com.github.benmanes.caffeine.cache.Cache<String, String> cache = caffeineNativeCache.expireAfterAccessCache();
        String v1 = cache.get(key, k -> slowValueService.compute("caffeine-eaa:" + k));
        for (int i = 0; i < touchCount; i++) {
            sleep(touchIntervalMs);
            cache.getIfPresent(key);
        }
        sleep(afterTouchSleepMs);
        String v2 = cache.get(key, k -> slowValueService.compute("caffeine-eaa:" + k));

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine");
        m.put("场景", "访问后过期 expireAfterAccess(30s)");
        m.put("key", key);
        m.put("第一次结果", v1);
        m.put("第二次结果", v2);
        m.put("是否相同", v1 != null && v1.equals(v2));
        m.put("触碰次数", touchCount);
        m.put("触碰间隔毫秒", touchIntervalMs);
        m.put("触碰后休眠毫秒", afterTouchSleepMs);
        m.put("统计", caffeineAccessStats());
        return m;
    }

    /**
     * 对比点：maximumSize（基于容量的淘汰）
     * - maximumSize 很小（这里=3），写入更多 key 后会触发淘汰
     * - Caffeine 的淘汰策略更先进（W-TinyLFU），但这里主要看“会淘汰”的现象
     */
    @GetMapping("/caffeine/scenario/maxSize")
    public Map<String, Object> caffeineMaxSize(@RequestParam(value = "count", defaultValue = "10") int count) {
        com.github.benmanes.caffeine.cache.Cache<String, String> cache = caffeineNativeCache.maxSize3Cache();
        cache.invalidateAll();
        for (int i = 0; i < count; i++) {
            String k = "k" + i;
            cache.put(k, slowValueService.compute("caffeine-maxsize:" + k));
        }

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine");
        m.put("场景", "容量淘汰 maximumSize(3)");
        m.put("写入key数量", count);
        m.put("估算当前大小", cache.estimatedSize());
        m.put("k0是否存在", cache.getIfPresent("k0") != null);
        m.put("统计", caffeineMaxSizeStats());
        return m;
    }

    /**
     * LoadingCache：把“如何加载数据”的逻辑固定在缓存里
     * - cache.get(key) 永远不会返回 null（loader 决定）
     */
    @GetMapping("/caffeine/loading/get")
    public Map<String, Object> caffeineLoadingGet(@RequestParam("key") String key) {
        LoadingCache<String, String> cache = caffeineNativeCache.loadingCache();
        String value = cache.get(key);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine");
        m.put("类型", "LoadingCache");
        m.put("key", key);
        m.put("值", value);
        m.put("统计", caffeineLoadingStats());
        return m;
    }

    /**
     * refresh：触发刷新（在 refreshAfterWrite 配置下）
     * - Caffeine 的 refresh 是异步的：refresh 后立刻 get 可能还是旧值
     * - 这里 sleep(waitMs) 只是为了更容易观察“值是否变化”
     */
    @GetMapping("/caffeine/loading/refresh")
    public Map<String, Object> caffeineLoadingRefresh(
        @RequestParam("key") String key,
        @RequestParam(value = "waitMs", defaultValue = "200") long waitMs
    ) {
        LoadingCache<String, String> cache = caffeineNativeCache.loadingCache();
        String before = cache.get(key);
        cache.refresh(key);
        sleep(waitMs);
        String after = cache.get(key);

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine");
        m.put("类型", "LoadingCache");
        m.put("动作", "refresh");
        m.put("key", key);
        m.put("刷新前", before);
        m.put("刷新后", after);
        m.put("是否相同", before != null && before.equals(after));
        m.put("等待毫秒", waitMs);
        m.put("统计", caffeineLoadingStats());
        return m;
    }

    /**
     * AsyncLoadingCache：异步加载/刷新，适合 IO 场景（比如远程调用）
     * - 这里为了简化展示，直接 join 等待结果返回
     */
    @GetMapping("/caffeine/async/get")
    public Map<String, Object> caffeineAsyncGet(@RequestParam("key") String key) {
        AsyncLoadingCache<String, String> cache = caffeineNativeCache.asyncLoadingCache();
        CompletableFuture<String> future = cache.get(key);
        String value = future.join();

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine");
        m.put("类型", "AsyncLoadingCache");
        m.put("key", key);
        m.put("值", value);
        m.put("统计", caffeineAsyncStats());
        return m;
    }

    /**
     * Guava 的基础用法：Cache.get(key, Callable)
     */
    @GetMapping("/guava/load")
    public Map<String, Object> guavaLoad(@RequestParam("key") String key) throws ExecutionException {
        Cache<String, String> cache = guavaNativeCache.cache();
        String value = cache.get(key, () -> slowValueService.compute("guava:" + key));
        return response("Guava", key, value);
    }

    /**
     * Guava 原生 put
     */
    @GetMapping("/guava/put")
    public Map<String, Object> guavaPut(@RequestParam("key") String key, @RequestParam("value") String value) {
        guavaNativeCache.cache().put(key, value);
        return response("Guava", key, value);
    }

    /**
     * Guava 原生 invalidate
     */
    @DeleteMapping("/guava/invalidate")
    public Map<String, Object> guavaInvalidate(@RequestParam("key") String key) {
        guavaNativeCache.cache().invalidate(key);
        return response("Guava", key, null);
    }

    /**
     * Guava 统计信息（需要 recordStats() 才会有）
     */
    @GetMapping("/guava/stats")
    public Map<String, Object> guavaStats() {
        com.google.common.cache.CacheStats stats = guavaNativeCache.cache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Guava(写后过期)");
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    /**
     * Guava：写后过期 expireAfterWrite
     */
    @GetMapping("/guava/scenario/expireAfterWrite")
    public Map<String, Object> guavaExpireAfterWrite(
        @RequestParam("key") String key,
        @RequestParam(value = "sleepMs", defaultValue = "35000") long sleepMs
    ) throws ExecutionException {
        Cache<String, String> cache = guavaNativeCache.cache();
        String v1 = cache.get(key, () -> slowValueService.compute("guava-eaw:" + key));
        sleep(sleepMs);
        String v2 = cache.get(key, () -> slowValueService.compute("guava-eaw:" + key));

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Guava");
        m.put("场景", "写后过期 expireAfterWrite(30s)");
        m.put("key", key);
        m.put("第一次结果", v1);
        m.put("第二次结果", v2);
        m.put("是否相同", v1 != null && v1.equals(v2));
        m.put("休眠毫秒", sleepMs);
        m.put("统计", guavaStats());
        return m;
    }

    /**
     * Guava：访问后过期 expireAfterAccess
     */
    @GetMapping("/guava/scenario/expireAfterAccess")
    public Map<String, Object> guavaExpireAfterAccess(
        @RequestParam("key") String key,
        @RequestParam(value = "touchCount", defaultValue = "3") int touchCount,
        @RequestParam(value = "touchIntervalMs", defaultValue = "8000") long touchIntervalMs,
        @RequestParam(value = "afterTouchSleepMs", defaultValue = "35000") long afterTouchSleepMs
    ) throws ExecutionException {
        Cache<String, String> cache = guavaNativeCache.expireAfterAccessCache();
        String v1 = cache.get(key, () -> slowValueService.compute("guava-eaa:" + key));
        for (int i = 0; i < touchCount; i++) {
            sleep(touchIntervalMs);
            cache.getIfPresent(key);
        }
        sleep(afterTouchSleepMs);
        String v2 = cache.get(key, () -> slowValueService.compute("guava-eaa:" + key));

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Guava");
        m.put("场景", "访问后过期 expireAfterAccess(30s)");
        m.put("key", key);
        m.put("第一次结果", v1);
        m.put("第二次结果", v2);
        m.put("是否相同", v1 != null && v1.equals(v2));
        m.put("触碰次数", touchCount);
        m.put("触碰间隔毫秒", touchIntervalMs);
        m.put("触碰后休眠毫秒", afterTouchSleepMs);
        m.put("统计", guavaAccessStats());
        return m;
    }

    /**
     * Guava：容量淘汰 maximumSize
     */
    @GetMapping("/guava/scenario/maxSize")
    public Map<String, Object> guavaMaxSize(@RequestParam(value = "count", defaultValue = "10") int count) {
        Cache<String, String> cache = guavaNativeCache.maxSize3Cache();
        cache.invalidateAll();
        for (int i = 0; i < count; i++) {
            String k = "k" + i;
            cache.put(k, slowValueService.compute("guava-maxsize:" + k));
        }

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Guava");
        m.put("场景", "容量淘汰 maximumSize(3)");
        m.put("写入key数量", count);
        m.put("当前大小", cache.size());
        m.put("k0是否存在", cache.getIfPresent("k0") != null);
        m.put("统计", guavaMaxSizeStats());
        return m;
    }

    /**
     * Guava LoadingCache：cache.get(key) 由 CacheLoader 决定如何加载
     */
    @GetMapping("/guava/loading/get")
    public Map<String, Object> guavaLoadingGet(@RequestParam("key") String key) throws ExecutionException {
        com.google.common.cache.LoadingCache<String, String> cache = guavaNativeCache.loadingCache();
        String value = cache.get(key);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Guava");
        m.put("类型", "LoadingCache");
        m.put("key", key);
        m.put("值", value);
        m.put("统计", guavaLoadingStats());
        return m;
    }

    /**
     * Guava refresh：触发刷新（在 refreshAfterWrite 配置下）
     * - Guava 的 refresh 也是异步行为：refresh 后立刻 get 可能还是旧值
     */
    @GetMapping("/guava/loading/refresh")
    public Map<String, Object> guavaLoadingRefresh(
        @RequestParam("key") String key,
        @RequestParam(value = "waitMs", defaultValue = "200") long waitMs
    ) throws ExecutionException {
        com.google.common.cache.LoadingCache<String, String> cache = guavaNativeCache.loadingCache();
        String before = cache.get(key);
        cache.refresh(key);
        sleep(waitMs);
        String after = cache.get(key);

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Guava");
        m.put("类型", "LoadingCache");
        m.put("动作", "refresh");
        m.put("key", key);
        m.put("刷新前", before);
        m.put("刷新后", after);
        m.put("是否相同", before != null && before.equals(after));
        m.put("等待毫秒", waitMs);
        m.put("统计", guavaLoadingStats());
        return m;
    }

    private static Map<String, Object> response(String cache, String key, String value) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", cache);
        m.put("key", key);
        m.put("值", value);
        return m;
    }

    private Map<String, Object> caffeineAccessStats() {
        CacheStats stats = caffeineNativeCache.expireAfterAccessCache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine(访问后过期)");
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    private Map<String, Object> caffeineMaxSizeStats() {
        CacheStats stats = caffeineNativeCache.maxSize3Cache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine(maxSize=3)");
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    private Map<String, Object> caffeineLoadingStats() {
        CacheStats stats = caffeineNativeCache.loadingCache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine(LoadingCache)");
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    private Map<String, Object> caffeineAsyncStats() {
        CacheStats stats = caffeineNativeCache.asyncLoadingCache().synchronous().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Caffeine(AsyncLoadingCache)");
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    private Map<String, Object> guavaAccessStats() {
        com.google.common.cache.CacheStats stats = guavaNativeCache.expireAfterAccessCache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Guava(访问后过期)");
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    private Map<String, Object> guavaMaxSizeStats() {
        com.google.common.cache.CacheStats stats = guavaNativeCache.maxSize3Cache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Guava(maxSize=3)");
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
    }

    private Map<String, Object> guavaLoadingStats() {
        com.google.common.cache.CacheStats stats = guavaNativeCache.loadingCache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("缓存", "Guava(LoadingCache)");
        m.put("请求数", stats.requestCount());
        m.put("命中数", stats.hitCount());
        m.put("未命中数", stats.missCount());
        m.put("命中率", stats.hitRate());
        m.put("未命中率", stats.missRate());
        m.put("淘汰数", stats.evictionCount());
        return m;
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
