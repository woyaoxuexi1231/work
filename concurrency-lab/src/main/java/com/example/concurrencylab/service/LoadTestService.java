package com.example.concurrencylab.service;

import com.example.concurrencylab.model.ProductStock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class LoadTestService {
    private final InMemoryStockService inMemoryStockService;
    private final PointsService pointsService;
    private final RedisDbStockService redisDbStockService;

    public record RunResult(
            String mode,
            int threads,
            int requests,
            int qty,
            long success,
            long fail,
            long durationMs,
            StateSnapshot state
    ) {}

    public record StateSnapshot(
            int unsafeStock,
            int lockedStock,
            int unsafePoints,
            long safePoints,
            long redisAvailable,
            Long dbAvailable,
            Long dbReservedForRedis,
            Long dbSold,
            long pendingOrders,
            long appliedOrders
    ) {}

    public RunResult run(RunSpec spec) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(spec.threads());
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch readyGate = new CountDownLatch(spec.threads());

        List<Future<Boolean>> futures = new ArrayList<>(spec.requests());
        for (int i = 0; i < spec.requests(); i++) {
            futures.add(pool.submit(() -> {
                readyGate.countDown();
                startGate.await();
                return executeOne(spec);
            }));
        }

        readyGate.await();
        Instant start = Instant.now();
        startGate.countDown();

        long success = 0;
        long fail = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                success++;
            } else {
                fail++;
            }
        }
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        pool.shutdownNow();

        return new RunResult(
                spec.mode(),
                spec.threads(),
                spec.requests(),
                spec.qty(),
                success,
                fail,
                durationMs,
                snapshot(spec.sku())
        );
    }

    private boolean executeOne(RunSpec spec) {
        return switch (spec.mode()) {
            case "STOCK_UNSAFE" -> inMemoryStockService.buyUnsafe(spec.qty());
            case "STOCK_LOCK" -> inMemoryStockService.buyLocked(spec.qty());
            case "POINTS_UNSAFE" -> {
                pointsService.addUnsafe(spec.qty());
                yield true;
            }
            case "POINTS_SAFE" -> {
                pointsService.addSafe(spec.qty());
                yield true;
            }
            case "REDIS_STOCK" -> redisDbStockService.buy(spec.sku(), spec.qty(), spec.replenishBatch()).success();
            default -> throw new IllegalArgumentException("unknown mode: " + spec.mode());
        };
    }

    private StateSnapshot snapshot(String sku) {
        long redisAvailable = sku == null ? 0L : redisDbStockService.getRedisAvailable(sku);
        Optional<ProductStock> db = sku == null ? Optional.empty() : redisDbStockService.getDbStock(sku);
        return new StateSnapshot(
                inMemoryStockService.getUnsafeStock(),
                inMemoryStockService.getLockedStock(),
                pointsService.getUnsafePoints(),
                pointsService.getSafePoints(),
                redisAvailable,
                db.map(ProductStock::getDbAvailable).orElse(null),
                db.map(ProductStock::getDbReservedForRedis).orElse(null),
                db.map(ProductStock::getDbSold).orElse(null),
                redisDbStockService.getPendingOrders(),
                redisDbStockService.getAppliedOrders()
        );
    }

    public record RunSpec(
            String mode,
            int threads,
            int requests,
            int qty,
            String sku,
            long replenishBatch
    ) {}
}
