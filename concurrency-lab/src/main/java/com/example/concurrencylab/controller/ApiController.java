package com.example.concurrencylab.controller;

import com.example.concurrencylab.service.InMemoryStockService;
import com.example.concurrencylab.service.LoadTestService;
import com.example.concurrencylab.service.PointsService;
import com.example.concurrencylab.service.RedisDbStockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {
    private final InMemoryStockService inMemoryStockService;
    private final PointsService pointsService;
    private final RedisDbStockService redisDbStockService;
    private final LoadTestService loadTestService;

    @PostMapping("/reset")
    public Map<String, Object> reset(@Valid @RequestBody ResetRequest req) {
        inMemoryStockService.reset(req.inMemoryStock());
        pointsService.reset();
        return Map.of("ok", true);
    }

    @PostMapping("/redis/init")
    public Map<String, Object> initRedisSku(@Valid @RequestBody RedisInitRequest req) {
        redisDbStockService.initSku(req.sku(), req.total(), req.prefetch());
        return Map.of("ok", true);
    }

    @PostMapping("/redis/buy")
    public RedisDbStockService.BuyResult buyRedis(@Valid @RequestBody RedisBuyRequest req) {
        long replenish = req.replenishBatch() == null ? 50L : req.replenishBatch();
        return redisDbStockService.buy(req.sku(), req.qty(), replenish);
    }

    @GetMapping("/state")
    public LoadTestService.StateSnapshot state(@RequestParam(required = false) String sku) {
        var dbOpt = sku == null ? java.util.Optional.<com.example.concurrencylab.model.ProductStock>empty() : redisDbStockService.getDbStock(sku);
        return new LoadTestService.StateSnapshot(
                inMemoryStockService.getUnsafeStock(),
                inMemoryStockService.getLockedStock(),
                pointsService.getUnsafePoints(),
                pointsService.getSafePoints(),
                sku == null ? 0L : redisDbStockService.getRedisAvailable(sku),
                dbOpt.map(s -> s.getDbAvailable()).orElse(null),
                dbOpt.map(s -> s.getDbReservedForRedis()).orElse(null),
                dbOpt.map(s -> s.getDbSold()).orElse(null),
                redisDbStockService.getPendingOrders(),
                redisDbStockService.getAppliedOrders()
        );
    }

    @PostMapping("/test/run")
    public LoadTestService.RunResult run(@Valid @RequestBody RunRequest req) throws Exception {
        String sku = req.sku();
        long replenish = req.replenishBatch() == null ? 50L : req.replenishBatch();
        LoadTestService.RunSpec spec = new LoadTestService.RunSpec(
                req.mode(),
                req.threads(),
                req.requests(),
                req.qty(),
                sku,
                replenish
        );
        return loadTestService.run(spec);
    }

    public record ResetRequest(@Min(0) int inMemoryStock) {}

    public record RedisInitRequest(
            @NotBlank String sku,
            @Min(0) long total,
            @Min(0) long prefetch
    ) {}

    public record RedisBuyRequest(
            @NotBlank String sku,
            @Min(1) long qty,
            Long replenishBatch
    ) {}

    public record RunRequest(
            @NotBlank String mode,
            @Min(1) int threads,
            @Min(1) int requests,
            @Min(1) int qty,
            String sku,
            Long replenishBatch
    ) {}
}
