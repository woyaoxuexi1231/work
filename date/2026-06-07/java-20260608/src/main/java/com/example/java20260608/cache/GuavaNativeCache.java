package com.example.java20260608.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class GuavaNativeCache {

    private final Cache<String, String> cache;

    public GuavaNativeCache() {
        this.cache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .recordStats()
            .build();
    }

    public Cache<String, String> cache() {
        return cache;
    }
}
