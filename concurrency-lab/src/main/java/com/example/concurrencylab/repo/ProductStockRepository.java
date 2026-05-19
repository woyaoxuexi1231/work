package com.example.concurrencylab.repo;

import com.example.concurrencylab.model.ProductStock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {
    Optional<ProductStock> findBySku(String sku);
}
