package com.example.concurrencylab.service;

public interface RedisLike {
    long getLong(String key);

    long incrBy(String key, long delta);

    boolean decrIfAtLeast(String key, long delta);
}
