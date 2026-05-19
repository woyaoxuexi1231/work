package com.example.redissonlocklab.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class IdempotencyService {
    private final ObjectProvider<RedissonClient> redissonProvider;
    private final InstanceId instanceId;

    public record Result(boolean firstTime, String key, String handledByInstanceId) {}

    public Result handleOnce(String bizKey, Duration ttl) {
        String key = "idem:" + bizKey;
        RBucket<String> bucket = redissonProvider.getObject().getBucket(key);
        long ttlMs = Math.max(1L, ttl.toMillis());
        boolean ok = bucket.trySet(instanceId.value(), ttlMs, TimeUnit.MILLISECONDS);
        String val = bucket.get();
        return new Result(ok, key, val);
    }
}
