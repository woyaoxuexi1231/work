package com.example.redissonlocklab.controller;

import com.example.redissonlocklab.service.IdempotencyService;
import com.example.redissonlocklab.service.InstanceId;
import com.example.redissonlocklab.service.LuaValueLockService;
import com.example.redissonlocklab.service.RedissonLockDemoService;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class LockLabController {
    private final InstanceId instanceId;
    private final RedissonLockDemoService redissonLockDemoService;
    private final LuaValueLockService luaValueLockService;
    private final IdempotencyService idempotencyService;

    @Value("${app.redisson.lock-watchdog-timeout-ms:30000}")
    private long lockWatchdogTimeoutMs;

    @GetMapping("/info")
    public Map<String, Object> info() {
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("instanceId", instanceId.value());
        info.put("redissonLockWatchdogTimeoutMs", lockWatchdogTimeoutMs);
        return info;
    }

    @PostMapping("/redisson/run")
    public RedissonLockDemoService.RunResult runRedisson(@Valid @RequestBody RedissonRunRequest req) throws InterruptedException {
        return redissonLockDemoService.run(req.getLockKey(), req.getWaitMs(), req.getLeaseMs(), req.getWorkMs());
    }

    @PostMapping("/lua/run")
    public LuaValueLockService.RunResult runLua(@Valid @RequestBody LuaRunRequest req) throws InterruptedException {
        return luaValueLockService.run(req.getLockKey(), req.getWaitMs(), req.getLeaseMs(), req.getRenewEveryMs(), req.getWorkMs());
    }

    @PostMapping("/ttl")
    public Map<String, Object> ttl(@Valid @RequestBody TtlRequest req) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("lockKey", req.getLockKey());
        result.put("ttlMs", luaValueLockService.ttlMs(req.getLockKey()));
        return result;
    }

    @PostMapping("/idem")
    public IdempotencyService.Result idem(@Valid @RequestBody IdemRequest req) {
        return idempotencyService.handleOnce(req.getBizKey(), Duration.ofSeconds(req.getTtlSeconds()));
    }

    public static class RedissonRunRequest {

        @NotBlank

        private final String lockKey;

        @Min(0)

        private final long waitMs;

        @Min(1)

        private final long workMs;

        private final Long leaseMs;


        public RedissonRunRequest(String lockKey, long waitMs, long workMs, Long leaseMs) {

            this.lockKey = lockKey;

            this.waitMs = waitMs;

            this.workMs = workMs;

            this.leaseMs = leaseMs;

        }


        public String getLockKey() { return lockKey; }

        public long getWaitMs() { return waitMs; }

        public long getWorkMs() { return workMs; }

        public Long getLeaseMs() { return leaseMs; }

    }

    public static class LuaRunRequest {

        @NotBlank

        private final String lockKey;

        @Min(0)

        private final long waitMs;

        @Min(1)

        private final long workMs;

        @Min(1)

        private final long leaseMs;

        private final Long renewEveryMs;


        public LuaRunRequest(String lockKey, long waitMs, long workMs, long leaseMs, Long renewEveryMs) {

            this.lockKey = lockKey;

            this.waitMs = waitMs;

            this.workMs = workMs;

            this.leaseMs = leaseMs;

            this.renewEveryMs = renewEveryMs;

        }


        public String getLockKey() { return lockKey; }

        public long getWaitMs() { return waitMs; }

        public long getWorkMs() { return workMs; }

        public long getLeaseMs() { return leaseMs; }

        public Long getRenewEveryMs() { return renewEveryMs; }

    }

    public static class TtlRequest {

        @NotBlank

        private final String lockKey;


        public TtlRequest(String lockKey) {

            this.lockKey = lockKey;

        }


        public String getLockKey() { return lockKey; }

    }

    public static class IdemRequest {

        @NotBlank

        private final String bizKey;

        @Min(1)

        private final long ttlSeconds;


        public IdemRequest(String bizKey, long ttlSeconds) {

            this.bizKey = bizKey;

            this.ttlSeconds = ttlSeconds;

        }


        public String getBizKey() { return bizKey; }

        public long getTtlSeconds() { return ttlSeconds; }

    }
}

