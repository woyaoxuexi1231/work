package com.example.java20260608.cache;

import com.example.java20260608.service.SlowValueService;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CaffeineNativeCache {

    /**
     * 写后过期（expireAfterWrite）：写入后开始计时，到期后失效；访问不会延长 TTL。
     */
    private final Cache<String, String> baseCache;
    /**
     * 访问后过期（expireAfterAccess）：每次访问都会刷新过期时间，更适合热点数据。
     */
    private final Cache<String, String> expireAfterAccessCache;
    /**
     * 容量淘汰（maximumSize=3）：用于观察淘汰发生与统计信息变化。
     */
    private final Cache<String, String> maxSize3Cache;
    /**
     * LoadingCache：把“如何加载数据”的逻辑固定在缓存里（get 时自动加载）。
     */
    private final LoadingCache<String, String> loadingCache;
    /**
     * AsyncLoadingCache：异步加载/刷新版本（更贴近 IO 场景）。
     */
    private final AsyncLoadingCache<String, String> asyncLoadingCache;

    public CaffeineNativeCache(SlowValueService slowValueService) {
        this.baseCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .recordStats()
            .build();

        this.expireAfterAccessCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofSeconds(30))
            .recordStats()
            .build();

        this.maxSize3Cache = Caffeine.newBuilder()
            .maximumSize(3)
            .recordStats()
            .build();

        this.loadingCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .refreshAfterWrite(Duration.ofSeconds(5))
            .recordStats()
            .build(k -> slowValueService.compute("caffeine-loading:" + k));

        this.asyncLoadingCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .refreshAfterWrite(Duration.ofSeconds(5))
            .recordStats()
            .buildAsync(k -> slowValueService.compute("caffeine-async:" + k));
    }

    public Cache<String, String> cache() {
        return baseCache;
    }

    public Cache<String, String> expireAfterAccessCache() {
        return expireAfterAccessCache;
    }

    public Cache<String, String> maxSize3Cache() {
        return maxSize3Cache;
    }

    public LoadingCache<String, String> loadingCache() {
        return loadingCache;
    }

    public AsyncLoadingCache<String, String> asyncLoadingCache() {
        return asyncLoadingCache;
    }
}
