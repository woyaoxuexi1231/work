package com.example.java20260608.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CaffeineNativeCache {

    private final Cache<String, String> cache;

    public CaffeineNativeCache() {
        this.cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .recordStats()
            .build();
    }

    public Cache<String, String> cache() {
        return cache;
    }
}
