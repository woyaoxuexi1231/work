package com.example.java20260608.config;

import com.example.java20260608.springcache.GuavaCacheManagerAdapter;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheManagersConfig {

    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("demo");
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .recordStats());
        return manager;
    }

    @Bean("guavaCacheManager")
    public CacheManager guavaCacheManager() {
        return new GuavaCacheManagerAdapter(10_000, 30, true, "demo");
    }
}
