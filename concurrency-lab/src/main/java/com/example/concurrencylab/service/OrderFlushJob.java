package com.example.concurrencylab.service;

import com.example.concurrencylab.model.OrderEvent;
import com.example.concurrencylab.model.OrderStatus;
import com.example.concurrencylab.model.ProductStock;
import com.example.concurrencylab.repo.OrderEventRepository;
import com.example.concurrencylab.repo.ProductStockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class OrderFlushJob {
    private static final Logger log = LoggerFactory.getLogger(OrderFlushJob.class);

    private final OrderEventRepository orderEventRepository;
    private final ProductStockRepository productStockRepository;

    @Scheduled(fixedDelay = 1000)
    public void flush() {
        List<OrderEvent> events = orderEventRepository.findByStatusOrderByIdAsc(
                OrderStatus.NEW,
                PageRequest.of(0, 200)
        );
        for (OrderEvent e : events) {
            try {
                applyOne(e.getId());
            } catch (RuntimeException ex) {
                log.warn("flush failed orderId={} err={}", e.getId(), ex.toString());
            }
        }
    }

    @Transactional
    public void applyOne(Long orderId) {
        OrderEvent event = orderEventRepository.findById(orderId).orElse(null);
        if (event == null || event.getStatus() != OrderStatus.NEW) {
            return;
        }

        ProductStock stock = productStockRepository.findBySku(event.getSku()).orElseThrow(() ->
                new IllegalStateException("sku not found: " + event.getSku())
        );

        long qty = event.getQty();
        if (stock.getDbReservedForRedis() < qty) {
            throw new IllegalStateException("reserved not enough: reserved=" + stock.getDbReservedForRedis() + " qty=" + qty);
        }

        stock.setDbReservedForRedis(stock.getDbReservedForRedis() - qty);
        stock.setDbSold(stock.getDbSold() + qty);
        productStockRepository.save(stock);

        event.setStatus(OrderStatus.APPLIED);
        orderEventRepository.save(event);

        log.info("order applied id={} sku={} qty={} dbAvailable={} dbReserved={} dbSold={}",
                event.getId(),
                event.getSku(),
                qty,
                stock.getDbAvailable(),
                stock.getDbReservedForRedis(),
                stock.getDbSold());
    }
}
