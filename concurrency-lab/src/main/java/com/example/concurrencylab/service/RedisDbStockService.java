package com.example.concurrencylab.service;

import com.example.concurrencylab.model.OrderEvent;
import com.example.concurrencylab.model.OrderStatus;
import com.example.concurrencylab.model.ProductStock;
import com.example.concurrencylab.repo.OrderEventRepository;
import com.example.concurrencylab.repo.ProductStockRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class RedisDbStockService {
    private static final Logger log = LoggerFactory.getLogger(RedisDbStockService.class);

    private static final String STOCK_KEY_PREFIX = "stock:";

    private final RedisLike redis;
    private final DbStockService dbStockService;
    private final ProductStockRepository productStockRepository;
    private final OrderEventRepository orderEventRepository;

    public record BuyResult(boolean success, String message, Long orderId) {}

    @Transactional
    public void initSku(String sku, long total, long prefetchToRedis) {
        dbStockService.upsertSku(sku, total);

        String key = stockKey(sku);
        long existing = redis.getLong(key);
        if (existing != 0) {
            redis.incrBy(key, -existing);
        }

        long moved = reserveFromDbToRedis(sku, prefetchToRedis);
        log.info("init sku={} total={} prefetch={} movedToRedis={}", sku, total, prefetchToRedis, moved);
    }

    public BuyResult buy(String sku, long qty, long replenishBatch) {
        String key = stockKey(sku);
        boolean ok = redis.decrIfAtLeast(key, qty);
        if (!ok) {
            long moved = reserveFromDbToRedis(sku, replenishBatch);
            if (moved > 0) {
                ok = redis.decrIfAtLeast(key, qty);
            }
        }
        if (!ok) {
            return new BuyResult(false, "SOLD_OUT", null);
        }

        try {
            Long orderId = createOrderEvent(sku, qty);
            return new BuyResult(true, "OK", orderId);
        } catch (RuntimeException e) {
            redis.incrBy(key, qty);
            throw e;
        }
    }

    public long reserveFromDbToRedis(String sku, long batch) {
        if (batch <= 0) {
            return 0L;
        }

        long moved = dbStockService.reserveFromDb(sku, batch);
        if (moved <= 0) {
            return 0L;
        }
        long after = redis.incrBy(stockKey(sku), moved);
        ProductStock stock = productStockRepository.findBySku(sku).orElse(null);
        if (stock != null) {
            log.info("reserve sku={} moved={} redisAfter={} dbAvailable={} dbReserved={}",
                    sku, moved, after, stock.getDbAvailable(), stock.getDbReservedForRedis());
        } else {
            log.info("reserve sku={} moved={} redisAfter={}", sku, moved, after);
        }
        return moved;
    }

    public long getRedisAvailable(String sku) {
        return redis.getLong(stockKey(sku));
    }

    public Optional<ProductStock> getDbStock(String sku) {
        return productStockRepository.findBySku(sku);
    }

    public long getPendingOrders() {
        return orderEventRepository.countByStatus(OrderStatus.NEW);
    }

    public long getAppliedOrders() {
        return orderEventRepository.countByStatus(OrderStatus.APPLIED);
    }

    private String stockKey(String sku) {
        return STOCK_KEY_PREFIX + sku;
    }

    @Transactional
    protected Long createOrderEvent(String sku, long qty) {
        OrderEvent event = new OrderEvent();
        event.setSku(sku);
        event.setQty(qty);
        event.setStatus(OrderStatus.NEW);
        OrderEvent saved = orderEventRepository.save(event);
        log.info("order created id={} sku={} qty={}", saved.getId(), sku, qty);
        return saved.getId();
    }
}
