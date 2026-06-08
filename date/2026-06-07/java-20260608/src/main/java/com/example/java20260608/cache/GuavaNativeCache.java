package com.example.java20260608.cache;

import com.example.java20260608.service.SlowValueService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class GuavaNativeCache {

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

    public GuavaNativeCache(SlowValueService slowValueService) {
        this.baseCache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .recordStats()
            .build();

        this.expireAfterAccessCache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(30, TimeUnit.SECONDS)
            .recordStats()
            .build();

        this.maxSize3Cache = CacheBuilder.newBuilder()
            .maximumSize(3)
            .recordStats()
            .build();

        this.loadingCache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .refreshAfterWrite(5, TimeUnit.SECONDS)
            .recordStats()
            .build(new CacheLoader<String, String>() {
                @Override
                public String load(String key) {
                    return slowValueService.compute("guava-loading:" + key);
                }
            });
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
}
