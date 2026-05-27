package com.example.redissonlocklab.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class LuaValueLockService {
    private static final StringCodec CODEC = StringCodec.INSTANCE;
    private static final HexFormat HEX = HexFormat.of();

    private static final String LUA_TRY_LOCK =
            "local ok = redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX')\n" +
            "if ok then\n" +
            "  return 1\n" +
            "else\n" +
            "  return 0\n" +
            "end";

    private static final String LUA_RENEW =
            "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
            "  redis.call('pexpire', KEYS[1], ARGV[2])\n" +
            "  return 1\n" +
            "else\n" +
            "  return 0\n" +
            "end";

    private static final String LUA_UNLOCK =
            "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
            "  return redis.call('del', KEYS[1])\n" +
            "else\n" +
            "  return 0\n" +
            "end";

    private final ObjectProvider<RedissonClient> redissonProvider;
    private final InstanceId instanceId;

    private final ScheduledExecutorService renewPool = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setName("lua-lock-renew");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, ScheduledFuture<?>> renewTasks = new ConcurrentHashMap<>();

    public static class RunResult {

        private final boolean acquired;

        private final String lockKey;

        private final String lockValue;

        private final long leaseMs;

        private final Long renewEveryMs;

        private final long workMs;

        private final long waitMs;

        private final long startedAtMs;

        private final long finishedAtMs;

        private final Long ttlAfterAcquireMs;

        private final Long ttlAfterFinishMs;

        private final long renewSuccessCount;

        private final long renewFailCount;


        public RunResult(boolean acquired, String lockKey, String lockValue, long leaseMs, Long renewEveryMs, long workMs, long waitMs, long startedAtMs, long finishedAtMs, Long ttlAfterAcquireMs, Long ttlAfterFinishMs, long renewSuccessCount, long renewFailCount) {

            this.acquired = acquired;

            this.lockKey = lockKey;

            this.lockValue = lockValue;

            this.leaseMs = leaseMs;

            this.renewEveryMs = renewEveryMs;

            this.workMs = workMs;

            this.waitMs = waitMs;

            this.startedAtMs = startedAtMs;

            this.finishedAtMs = finishedAtMs;

            this.ttlAfterAcquireMs = ttlAfterAcquireMs;

            this.ttlAfterFinishMs = ttlAfterFinishMs;

            this.renewSuccessCount = renewSuccessCount;

            this.renewFailCount = renewFailCount;

        }


        public boolean isAcquired() { return acquired; }

        public String getLockKey() { return lockKey; }

        public String getLockValue() { return lockValue; }

        public long getLeaseMs() { return leaseMs; }

        public Long getRenewEveryMs() { return renewEveryMs; }

        public long getWorkMs() { return workMs; }

        public long getWaitMs() { return waitMs; }

        public long getStartedAtMs() { return startedAtMs; }

        public long getFinishedAtMs() { return finishedAtMs; }

        public Long getTtlAfterAcquireMs() { return ttlAfterAcquireMs; }

        public Long getTtlAfterFinishMs() { return ttlAfterFinishMs; }

        public long getRenewSuccessCount() { return renewSuccessCount; }

        public long getRenewFailCount() { return renewFailCount; }

    }

    public RunResult run(String lockKey, long waitMs, long leaseMs, Long renewEveryMs, long workMs) throws InterruptedException {
        String lockValue = instanceId.value() + ":" + randomToken();
        long startedAtMs = Instant.now().toEpochMilli();

        boolean acquired = tryLockSpin(lockKey, lockValue, leaseMs, waitMs);
        if (!acquired) {
            return new RunResult(false, lockKey, lockValue, leaseMs, renewEveryMs, workMs, waitMs, startedAtMs, startedAtMs, null, null, 0, 0);
        }

        Long ttlAfterAcquireMs = ttlMs(lockKey);

        RenewCounter counter = new RenewCounter();
        if (renewEveryMs != null && renewEveryMs > 0) {
            startRenew(lockKey, lockValue, leaseMs, renewEveryMs, counter);
        }

        try {
            Thread.sleep(workMs);
        } finally {
            stopRenew(lockKey, lockValue);
            unlock(lockKey, lockValue);
        }

        long finishedAtMs = Instant.now().toEpochMilli();
        Long ttlAfterFinishMs = ttlMs(lockKey);
        return new RunResult(
                true,
                lockKey,
                lockValue,
                leaseMs,
                renewEveryMs,
                workMs,
                waitMs,
                startedAtMs,
                finishedAtMs,
                ttlAfterAcquireMs,
                ttlAfterFinishMs,
                counter.renewSuccess,
                counter.renewFail
        );
    }

    public boolean unlock(String lockKey, String lockValue) {
        RScript script = redissonProvider.getObject().getScript(CODEC);
        Object res = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_UNLOCK,
                RScript.ReturnType.INTEGER,
                java.util.List.of(lockKey),
                lockValue
        );
        return Objects.equals(res, 1L);
    }

    public Long ttlMs(String lockKey) {
        try {
            long ttl = redissonProvider.getObject().getKeys().remainTimeToLive(lockKey);
            return ttl < 0 ? null : ttl;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean tryLockSpin(String lockKey, String lockValue, long leaseMs, long waitMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0L, waitMs);
        do {
            if (tryLockOnce(lockKey, lockValue, leaseMs)) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            Thread.sleep(30);
        } while (true);
    }

    private boolean tryLockOnce(String lockKey, String lockValue, long leaseMs) {
        RScript script = redissonProvider.getObject().getScript(CODEC);
        Object res = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_TRY_LOCK,
                RScript.ReturnType.INTEGER,
                java.util.List.of(lockKey),
                lockValue,
                String.valueOf(leaseMs)
        );
        return Objects.equals(res, 1L);
    }

    private void startRenew(String lockKey, String lockValue, long leaseMs, long renewEveryMs, RenewCounter counter) {
        String taskKey = lockKey + "|" + lockValue;
        renewTasks.computeIfAbsent(taskKey, k -> renewPool.scheduleWithFixedDelay(() -> {
            boolean ok = renew(lockKey, lockValue, leaseMs);
            if (ok) {
                counter.renewSuccess++;
            } else {
                counter.renewFail++;
            }
        }, renewEveryMs, renewEveryMs, TimeUnit.MILLISECONDS));
    }

    private void stopRenew(String lockKey, String lockValue) {
        String taskKey = lockKey + "|" + lockValue;
        ScheduledFuture<?> f = renewTasks.remove(taskKey);
        if (f != null) {
            f.cancel(false);
        }
    }

    private boolean renew(String lockKey, String lockValue, long leaseMs) {
        RScript script = redissonProvider.getObject().getScript(CODEC);
        Object res = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_RENEW,
                RScript.ReturnType.INTEGER,
                java.util.List.of(lockKey),
                lockValue,
                String.valueOf(leaseMs)
        );
        return Objects.equals(res, 1L);
    }

    private String randomToken() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return HEX.formatHex(b);
    }

    private static final class RenewCounter {
        volatile long renewSuccess;
        volatile long renewFail;
    }
}
