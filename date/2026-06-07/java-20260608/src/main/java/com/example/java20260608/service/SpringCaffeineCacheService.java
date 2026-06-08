package com.example.java20260608.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SpringCaffeineCacheService {

    private final SlowValueService slowValueService;

    public SpringCaffeineCacheService(SlowValueService slowValueService) {
        this.slowValueService = slowValueService;
    }

    @Cacheable(cacheNames = "demo", key = "#key", cacheManager = "caffeineCacheManager")
    public String load(String key) {
        return slowValueService.compute("spring-caffeine:" + key);
    }

    @CacheEvict(cacheNames = "demo", key = "#key", cacheManager = "caffeineCacheManager")
    public void evict(String key) {
    }

    @CacheEvict(cacheNames = "demo", allEntries = true, cacheManager = "caffeineCacheManager")
    public void clear() {
    }
}
