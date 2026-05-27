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
        inMemoryStockService.reset(req.getInMemoryStock());
        pointsService.reset();
        return Map.of("ok", true);
    }

    @PostMapping("/redis/init")
    public Map<String, Object> initRedisSku(@Valid @RequestBody RedisInitRequest req) {
        redisDbStockService.initSku(req.getSku(), req.getTotal(), req.getPrefetch());
        return Map.of("ok", true);
    }

    @PostMapping("/redis/buy")
    public RedisDbStockService.BuyResult buyRedis(@Valid @RequestBody RedisBuyRequest req) {
        long replenish = req.getReplenishBatch() == null ? 50L : req.getReplenishBatch();
        return redisDbStockService.buy(req.getSku(), req.getQty(), replenish);
    }

    @GetMapping("/state")
    public LoadTestService.StateSnapshot state(@RequestParam(required = false) String sku) {
        java.util.Optional<com.example.concurrencylab.model.ProductStock> dbOpt = sku == null ? java.util.Optional.<com.example.concurrencylab.model.ProductStock>empty() : redisDbStockService.getDbStock(sku);
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
        String sku = req.getSku();
        long replenish = req.getReplenishBatch() == null ? 50L : req.getReplenishBatch();
        LoadTestService.RunSpec spec = new LoadTestService.RunSpec(
                req.getMode(),
                req.getThreads(),
                req.getRequests(),
                req.getQty(),
                sku,
                replenish
        );
        return loadTestService.run(spec);
    }

    public static class ResetRequest {

        @Min(0)

        private final int inMemoryStock;


        public ResetRequest(int inMemoryStock) {

            this.inMemoryStock = inMemoryStock;

        }


        public int getInMemoryStock() { return inMemoryStock; }

    }

    public static class RedisInitRequest {

        @NotBlank

        private final String sku;

        @Min(0)

        private final long total;

        @Min(0)

        private final long prefetch;


        public RedisInitRequest(String sku, long total, long prefetch) {

            this.sku = sku;

            this.total = total;

            this.prefetch = prefetch;

        }


        public String getSku() { return sku; }

        public long getTotal() { return total; }

        public long getPrefetch() { return prefetch; }

    }

    public static class RedisBuyRequest {

        @NotBlank

        private final String sku;

        @Min(1)

        private final long qty;

        private final Long replenishBatch;


        public RedisBuyRequest(String sku, long qty, Long replenishBatch) {

            this.sku = sku;

            this.qty = qty;

            this.replenishBatch = replenishBatch;

        }


        public String getSku() { return sku; }

        public long getQty() { return qty; }

        public Long getReplenishBatch() { return replenishBatch; }

    }

    public static class RunRequest {

        @NotBlank

        private final String mode;

        @Min(1)

        private final int threads;

        @Min(1)

        private final int requests;

        @Min(1)

        private final int qty;

        private final String sku;

        private final Long replenishBatch;


        public RunRequest(String mode, int threads, int requests, int qty, String sku, Long replenishBatch) {

            this.mode = mode;

            this.threads = threads;

            this.requests = requests;

            this.qty = qty;

            this.sku = sku;

            this.replenishBatch = replenishBatch;

        }


        public String getMode() { return mode; }

        public int getThreads() { return threads; }

        public int getRequests() { return requests; }

        public int getQty() { return qty; }

        public String getSku() { return sku; }

        public Long getReplenishBatch() { return replenishBatch; }

    }
}
