package com.example.concurrencylab.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRedisLike implements RedisLike {
    private final ConcurrentHashMap<String, AtomicLong> data = new ConcurrentHashMap<>();

    @Override
    public long getLong(String key) {
        AtomicLong v = data.get(key);
        return v == null ? 0L : v.get();
    }

    @Override
    public long incrBy(String key, long delta) {
        return data.computeIfAbsent(key, k -> new AtomicLong(0L)).addAndGet(delta);
    }

    @Override
    public boolean decrIfAtLeast(String key, long delta) {
        AtomicLong v = data.computeIfAbsent(key, k -> new AtomicLong(0L));
        synchronized (v) {
            long cur = v.get();
            if (cur < delta) {
                return false;
            }
            v.set(cur - delta);
            return true;
        }
    }
}
