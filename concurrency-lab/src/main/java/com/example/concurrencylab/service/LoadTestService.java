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

    public static class RunResult {

        private final String mode;

        private final int threads;

        private final int requests;

        private final int qty;

        private final long success;

        private final long fail;

        private final long durationMs;

        private final StateSnapshot state;


        public RunResult(String mode, int threads, int requests, int qty, long success, long fail, long durationMs, StateSnapshot state) {

            this.mode = mode;

            this.threads = threads;

            this.requests = requests;

            this.qty = qty;

            this.success = success;

            this.fail = fail;

            this.durationMs = durationMs;

            this.state = state;

        }


        public String getMode() { return mode; }

        public int getThreads() { return threads; }

        public int getRequests() { return requests; }

        public int getQty() { return qty; }

        public long getSuccess() { return success; }

        public long getFail() { return fail; }

        public long getDurationMs() { return durationMs; }

        public StateSnapshot getState() { return state; }

    }

    public static class StateSnapshot {

        private final int unsafeStock;

        private final int lockedStock;

        private final int unsafePoints;

        private final long safePoints;

        private final long redisAvailable;

        private final Long dbAvailable;

        private final Long dbReservedForRedis;

        private final Long dbSold;

        private final long pendingOrders;

        private final long appliedOrders;


        public StateSnapshot(int unsafeStock, int lockedStock, int unsafePoints, long safePoints, long redisAvailable, Long dbAvailable, Long dbReservedForRedis, Long dbSold, long pendingOrders, long appliedOrders) {

            this.unsafeStock = unsafeStock;

            this.lockedStock = lockedStock;

            this.unsafePoints = unsafePoints;

            this.safePoints = safePoints;

            this.redisAvailable = redisAvailable;

            this.dbAvailable = dbAvailable;

            this.dbReservedForRedis = dbReservedForRedis;

            this.dbSold = dbSold;

            this.pendingOrders = pendingOrders;

            this.appliedOrders = appliedOrders;

        }


        public int getUnsafeStock() { return unsafeStock; }

        public int getLockedStock() { return lockedStock; }

        public int getUnsafePoints() { return unsafePoints; }

        public long getSafePoints() { return safePoints; }

        public long getRedisAvailable() { return redisAvailable; }

        public Long getDbAvailable() { return dbAvailable; }

        public Long getDbReservedForRedis() { return dbReservedForRedis; }

        public Long getDbSold() { return dbSold; }

        public long getPendingOrders() { return pendingOrders; }

        public long getAppliedOrders() { return appliedOrders; }

    }

    public RunResult run(RunSpec spec) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(spec.getThreads());
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch readyGate = new CountDownLatch(spec.getThreads());

        List<Future<Boolean>> futures = new ArrayList<>(spec.getRequests());
        for (int i = 0; i < spec.getRequests(); i++) {
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
                spec.getMode(),
                spec.getThreads(),
                spec.getRequests(),
                spec.getQty(),
                success,
                fail,
                durationMs,
                snapshot(spec.getSku())
        );
    }

    private boolean executeOne(RunSpec spec) {
        switch (spec.getMode()) {
            case "STOCK_UNSAFE":
                return inMemoryStockService.buyUnsafe(spec.getQty());
            case "STOCK_LOCK":
                return inMemoryStockService.buyLocked(spec.getQty());
            case "POINTS_UNSAFE":
                pointsService.addUnsafe(spec.getQty());
                return true;
            case "POINTS_SAFE":
                pointsService.addSafe(spec.getQty());
                return true;
            case "REDIS_STOCK":
                return redisDbStockService.buy(spec.getSku(), spec.getQty(), spec.getReplenishBatch()).isSuccess();
            default:
                throw new IllegalArgumentException("unknown mode: " + spec.getMode());
        }
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

    public static class RunSpec {

        private final String mode;

        private final int threads;

        private final int requests;

        private final int qty;

        private final String sku;

        private final long replenishBatch;


        public RunSpec(String mode, int threads, int requests, int qty, String sku, long replenishBatch) {

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

        public long getReplenishBatch() { return replenishBatch; }

    }
}
