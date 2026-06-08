package com.example.java20260608.controller;

import com.example.java20260608.cache.CaffeineNativeCache;
import com.example.java20260608.cache.GuavaNativeCache;
import com.example.java20260608.service.SlowValueService;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.cache.Cache;
import java.util.LinkedHashMap;
import java.util.Map;
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

    @GetMapping("/caffeine/load")
    public Map<String, Object> caffeineLoad(@RequestParam("key") String key) {
        String value = caffeineNativeCache.cache().get(key, slowValueService::compute);
        return response("caffeine", key, value);
    }

    @GetMapping("/caffeine/put")
    public Map<String, Object> caffeinePut(@RequestParam("key") String key, @RequestParam("value") String value) {
        caffeineNativeCache.cache().put(key, value);
        return response("caffeine", key, value);
    }

    @DeleteMapping("/caffeine/invalidate")
    public Map<String, Object> caffeineInvalidate(@RequestParam("key") String key) {
        caffeineNativeCache.cache().invalidate(key);
        return response("caffeine", key, null);
    }

    @GetMapping("/caffeine/stats")
    public Map<String, Object> caffeineStats() {
        CacheStats stats = caffeineNativeCache.cache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("cache", "caffeine");
        m.put("requestCount", stats.requestCount());
        m.put("hitCount", stats.hitCount());
        m.put("missCount", stats.missCount());
        m.put("hitRate", stats.hitRate());
        m.put("missRate", stats.missRate());
        m.put("evictionCount", stats.evictionCount());
        return m;
    }

    @GetMapping("/guava/load")
    public Map<String, Object> guavaLoad(@RequestParam("key") String key) throws ExecutionException {
        Cache<String, String> cache = guavaNativeCache.cache();
        String value = cache.get(key, () -> slowValueService.compute(key));
        return response("guava", key, value);
    }

    @GetMapping("/guava/put")
    public Map<String, Object> guavaPut(@RequestParam("key") String key, @RequestParam("value") String value) {
        guavaNativeCache.cache().put(key, value);
        return response("guava", key, value);
    }

    @DeleteMapping("/guava/invalidate")
    public Map<String, Object> guavaInvalidate(@RequestParam("key") String key) {
        guavaNativeCache.cache().invalidate(key);
        return response("guava", key, null);
    }

    @GetMapping("/guava/stats")
    public Map<String, Object> guavaStats() {
        com.google.common.cache.CacheStats stats = guavaNativeCache.cache().stats();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("cache", "guava");
        m.put("requestCount", stats.requestCount());
        m.put("hitCount", stats.hitCount());
        m.put("missCount", stats.missCount());
        m.put("hitRate", stats.hitRate());
        m.put("missRate", stats.missRate());
        m.put("evictionCount", stats.evictionCount());
        return m;
    }

    private static Map<String, Object> response(String cache, String key, String value) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("cache", cache);
        m.put("key", key);
        m.put("value", value);
        return m;
    }
}
