package com.demo.jdk21.controller;

import com.demo.jdk21.dto.OrderDTO;
import com.demo.jdk21.dto.OrderStatus;
import com.demo.jdk21.service.OrderService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * <h3>JDK 21 / Spring Boot 3 Controller</h3>
 *
 * <p>Spring Boot 3.2+ 配置虚线程：spring.threads.virtual.enabled=true</p>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderDTO createOrder(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request.orderNo(), request.amount());
    }

    public record CreateOrderRequest(
            @NotNull(message = "订单号不能为空") String orderNo,
            @NotNull(message = "金额不能为空") @Positive(message = "金额必须为正数") BigDecimal amount
    ) {}

    @GetMapping
    public List<OrderDTO> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable Long id) {
        return orderService.getOrder(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ---- 特性对比入口 ----

    /** 🔥 核心：虚线程并发（对比 JDK 8/17 的 CompletableFuture） */
    @GetMapping("/{id}/detail")
    public Map<String, Object> getOrderDetail(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = orderService.batchQueryExternal(id);
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("jdkVersion", "21");
        return result;
    }

    /** 🔥 高并发压测：虚线程 vs 平台线程内存对比 */
    @GetMapping("/concurrency-test")
    public Map<String, Object> concurrencyTest(@RequestParam(defaultValue = "100") int count) {
        return orderService.highConcurrencyDemo(count);
    }

    /** Record Pattern 解构（对比 JDK 17 instanceof 只绑定整个对象） */
    @GetMapping("/describe")
    public String describe(@RequestParam(defaultValue = "order") String type) {
        if ("status".equals(type)) {
            return orderService.describeOrder(new OrderStatus.Paid(new BigDecimal("99.99")));
        }
        return orderService.describeOrder(
                orderService.getOrder(1L).orElse(
                        new OrderDTO(0L, "DEMO-001", new BigDecimal("88.88"),
                                new OrderStatus.Pending(), null)));
    }

    /** Sequenced Collections（JDK 21 新增 getFirst/getLast/reversed） */
    @GetMapping("/sequenced")
    public Map<String, Object> sequencedDemo() {
        return orderService.sequencedCollectionDemo();
    }
}
