package com.example.redissonlocklab.service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RedissonLockDemoService {
    private final ObjectProvider<RedissonClient> redissonProvider;

    public record RunResult(
            boolean acquired,
            String lockKey,
            Long leaseMs,
            long workMs,
            long waitMs,
            long startedAtMs,
            long finishedAtMs,
            Long ttlAfterAcquireMs,
            Long ttlAfterFinishMs
    ) {}

    public RunResult run(String lockKey, long waitMs, Long leaseMs, long workMs) throws InterruptedException {
        RLock lock = redissonProvider.getObject().getLock(lockKey);

        boolean acquired;
        if (leaseMs == null) {
            acquired = lock.tryLock(waitMs, TimeUnit.MILLISECONDS);
        } else {
            acquired = lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
        }

        long startedAtMs = Instant.now().toEpochMilli();
        if (!acquired) {
            return new RunResult(false, lockKey, leaseMs, workMs, waitMs, startedAtMs, startedAtMs, null, null);
        }

        Long ttlAfterAcquireMs = safeTtl(lock);
        try {
            Thread.sleep(workMs);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        long finishedAtMs = Instant.now().toEpochMilli();
        Long ttlAfterFinishMs = safeTtl(lock);
        return new RunResult(true, lockKey, leaseMs, workMs, waitMs, startedAtMs, finishedAtMs, ttlAfterAcquireMs, ttlAfterFinishMs);
    }

    private Long safeTtl(RLock lock) {
        try {
            long ttl = lock.remainTimeToLive();
            return ttl < 0 ? null : ttl;
        } catch (Exception e) {
            return null;
        }
    }
}
