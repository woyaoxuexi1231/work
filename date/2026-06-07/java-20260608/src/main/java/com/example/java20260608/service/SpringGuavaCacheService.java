package com.example.java20260608.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SpringGuavaCacheService {

    private final SlowValueService slowValueService;

    public SpringGuavaCacheService(SlowValueService slowValueService) {
        this.slowValueService = slowValueService;
    }

    @Cacheable(cacheNames = "demo", key = "#key", cacheManager = "guavaCacheManager")
    public String load(String key) {
        return slowValueService.compute("spring-guava:" + key);
    }

    @CacheEvict(cacheNames = "demo", key = "#key", cacheManager = "guavaCacheManager")
    public void evict(String key) {
    }

    @CacheEvict(cacheNames = "demo", allEntries = true, cacheManager = "guavaCacheManager")
    public void clear() {
    }
}
