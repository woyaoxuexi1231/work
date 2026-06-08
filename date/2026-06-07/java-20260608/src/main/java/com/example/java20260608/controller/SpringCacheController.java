package com.example.java20260608.controller;

import com.example.java20260608.service.SpringCaffeineCacheService;
import com.example.java20260608.service.SpringGuavaCacheService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/spring")
public class SpringCacheController {

    private final SpringCaffeineCacheService springCaffeineCacheService;
    private final SpringGuavaCacheService springGuavaCacheService;

    public SpringCacheController(
        SpringCaffeineCacheService springCaffeineCacheService,
        SpringGuavaCacheService springGuavaCacheService
    ) {
        this.springCaffeineCacheService = springCaffeineCacheService;
        this.springGuavaCacheService = springGuavaCacheService;
    }

    @GetMapping("/caffeine/load")
    public Map<String, Object> caffeineLoad(@RequestParam("key") String key) {
        String value = springCaffeineCacheService.load(key);
        return response("spring-caffeine", key, value);
    }

    @DeleteMapping("/caffeine/evict")
    public Map<String, Object> caffeineEvict(@RequestParam("key") String key) {
        springCaffeineCacheService.evict(key);
        return response("spring-caffeine", key, null);
    }

    @DeleteMapping("/caffeine/clear")
    public Map<String, Object> caffeineClear() {
        springCaffeineCacheService.clear();
        return response("spring-caffeine", "*", null);
    }

    @GetMapping("/guava/load")
    public Map<String, Object> guavaLoad(@RequestParam("key") String key) {
        String value = springGuavaCacheService.load(key);
        return response("spring-guava", key, value);
    }

    @DeleteMapping("/guava/evict")
    public Map<String, Object> guavaEvict(@RequestParam("key") String key) {
        springGuavaCacheService.evict(key);
        return response("spring-guava", key, null);
    }

    @DeleteMapping("/guava/clear")
    public Map<String, Object> guavaClear() {
        springGuavaCacheService.clear();
        return response("spring-guava", "*", null);
    }

    private static Map<String, Object> response(String cache, String key, String value) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("cache", cache);
        m.put("key", key);
        m.put("value", value);
        return m;
    }
}
