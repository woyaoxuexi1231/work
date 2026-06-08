package com.example.java20260608.springcache;

import com.google.common.cache.CacheBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public class GuavaCacheManagerAdapter implements CacheManager {

    private final long maximumSize;
    private final long expireAfterWriteSeconds;
    private final boolean recordStats;
    private final ConcurrentMap<String, Cache> caches;
    private final Collection<String> cacheNames;

    public GuavaCacheManagerAdapter(long maximumSize, long expireAfterWriteSeconds, boolean recordStats, String... cacheNames) {
        this.maximumSize = maximumSize;
        this.expireAfterWriteSeconds = expireAfterWriteSeconds;
        this.recordStats = recordStats;
        this.caches = new ConcurrentHashMap<String, Cache>();
        this.cacheNames = cacheNames == null ? Collections.<String>emptyList() : Arrays.asList(cacheNames);
        for (String name : this.cacheNames) {
            if (name != null && !name.trim().isEmpty()) {
                this.caches.put(name, createCache(name));
            }
        }
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = caches.get(name);
        if (cache != null) {
            return cache;
        }
        Cache created = createCache(name);
        Cache existing = caches.putIfAbsent(name, created);
        return existing == null ? created : existing;
    }

    @Override
    public Collection<String> getCacheNames() {
        if (!cacheNames.isEmpty()) {
            return Collections.unmodifiableCollection(cacheNames);
        }
        return Collections.unmodifiableCollection(caches.keySet());
    }

    private Cache createCache(String name) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(expireAfterWriteSeconds, TimeUnit.SECONDS);
        if (recordStats) {
            builder = builder.recordStats();
        }
        return new GuavaSpringCache(name, builder.build());
    }

    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("maximumSize", maximumSize);
        m.put("expireAfterWriteSeconds", expireAfterWriteSeconds);
        m.put("recordStats", recordStats);
        m.put("cacheNames", cacheNames);
        return m;
    }
}
