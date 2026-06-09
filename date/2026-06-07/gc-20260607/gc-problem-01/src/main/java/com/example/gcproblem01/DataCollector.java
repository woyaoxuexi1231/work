package com.example.gcproblem01;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * gc-problem-01: 观察老年代使用率是否持续上涨
 */
@RestController
public class DataCollector {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 每隔 2 秒执行一次
     */
    @Scheduled(fixedRate = 2000)
    public void collectData() {
        String key = "data-" + counter.incrementAndGet();
        // 每次存储约 100KB 数据
        byte[] payload = new byte[100 * 1024];
        store.put(key, payload);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("totalEntries", store.size());
        long totalBytes = store.values().stream().mapToLong(b -> b.length).sum();
        result.put("totalBytesMB", totalBytes / 1024 / 1024);
        return result;
    }

    @GetMapping("/trigger-gc")
    public String triggerGc() {
        System.gc();
        return "System.gc() called. Total entries in store: " + store.size();
    }
}
