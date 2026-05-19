package com.example.redissonlocklab.controller;

import com.example.redissonlocklab.service.IdempotencyService;
import com.example.redissonlocklab.service.InstanceId;
import com.example.redissonlocklab.service.LuaValueLockService;
import com.example.redissonlocklab.service.RedissonLockDemoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
        return Map.of(
                "instanceId", instanceId.value(),
                "redissonLockWatchdogTimeoutMs", lockWatchdogTimeoutMs
        );
    }

    @PostMapping("/redisson/run")
    public RedissonLockDemoService.RunResult runRedisson(@Valid @RequestBody RedissonRunRequest req) throws InterruptedException {
        return redissonLockDemoService.run(req.lockKey(), req.waitMs(), req.leaseMs(), req.workMs());
    }

    @PostMapping("/lua/run")
    public LuaValueLockService.RunResult runLua(@Valid @RequestBody LuaRunRequest req) throws InterruptedException {
        return luaValueLockService.run(req.lockKey(), req.waitMs(), req.leaseMs(), req.renewEveryMs(), req.workMs());
    }

    @PostMapping("/ttl")
    public Map<String, Object> ttl(@Valid @RequestBody TtlRequest req) {
        return Map.of(
                "lockKey", req.lockKey(),
                "ttlMs", luaValueLockService.ttlMs(req.lockKey())
        );
    }

    @PostMapping("/idem")
    public IdempotencyService.Result idem(@Valid @RequestBody IdemRequest req) {
        return idempotencyService.handleOnce(req.bizKey(), Duration.ofSeconds(req.ttlSeconds()));
    }

    public record RedissonRunRequest(
            @NotBlank String lockKey,
            @Min(0) long waitMs,
            @Min(1) long workMs,
            Long leaseMs
    ) {}

    public record LuaRunRequest(
            @NotBlank String lockKey,
            @Min(0) long waitMs,
            @Min(1) long workMs,
            @Min(1) long leaseMs,
            Long renewEveryMs
    ) {}

    public record TtlRequest(@NotBlank String lockKey) {}

    public record IdemRequest(@NotBlank String bizKey, @Min(1) long ttlSeconds) {}
}

