package com.example.concurrencylab.repo;

import com.example.concurrencylab.model.OrderEvent;
import com.example.concurrencylab.model.OrderStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    List<OrderEvent> findByStatusOrderByIdAsc(OrderStatus status, Pageable pageable);
    long countByStatus(OrderStatus status);
}
