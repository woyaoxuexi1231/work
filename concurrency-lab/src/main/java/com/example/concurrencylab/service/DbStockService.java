package com.example.concurrencylab.service;

import com.example.concurrencylab.model.ProductStock;
import com.example.concurrencylab.repo.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DbStockService {
    private final ProductStockRepository productStockRepository;

    @Transactional
    public ProductStock upsertSku(String sku, long total) {
        ProductStock stock = productStockRepository.findBySku(sku).orElseGet(() -> {
            ProductStock ps = new ProductStock();
            ps.setSku(sku);
            return ps;
        });
        stock.setDbAvailable(total);
        stock.setDbReservedForRedis(0);
        stock.setDbSold(0);
        return productStockRepository.save(stock);
    }

    @Transactional
    public long reserveFromDb(String sku, long batch) {
        if (batch <= 0) {
            return 0L;
        }
        ProductStock stock = productStockRepository.findBySku(sku).orElseThrow(() ->
                new IllegalArgumentException("sku not found: " + sku)
        );
        long canMove = Math.min(batch, stock.getDbAvailable());
        if (canMove <= 0) {
            return 0L;
        }
        stock.setDbAvailable(stock.getDbAvailable() - canMove);
        stock.setDbReservedForRedis(stock.getDbReservedForRedis() + canMove);
        productStockRepository.save(stock);
        return canMove;
    }
}
