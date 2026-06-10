package com.demo.jdk8.controller;

import com.demo.jdk8.dto.OrderDTO;
import com.demo.jdk8.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * <h3>JDK 8 / Spring Boot 2 Controller</h3>
 *
 * <p>注意 javax 命名空间 → Spring Boot 3 变成 jakarta</p>
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
        return orderService.createOrder(request.orderNo, request.amount);
    }

    // JDK 8：需要单独的 Request 类（对比 JDK 17 内联 Record）
    public static class CreateOrderRequest {
        @NotNull(message = "订单号不能为空")
        public String orderNo;
        @NotNull(message = "金额不能为空") @Positive(message = "金额必须为正数")
        public BigDecimal amount;
    }

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

    /** 痛点1：instanceof 两步走 → JDK 17 模式匹配一步到位 */
    @GetMapping("/describe")
    public String describe(@RequestParam(defaultValue = "hello") String input) {
        return orderService.describeObject(input);
    }

    /** 痛点2：switch 字符串 → JDK 17 switch 表达式 + sealed 穷举 */
    @GetMapping("/status-desc")
    public String statusDescription(@RequestParam(defaultValue = "PENDING") String status) {
        return orderService.getOrderStatusDescription(status);
    }

    /** 痛点3：CompletableFuture → JDK 21 虚线程 */
    @GetMapping("/{id}/detail")
    public Map<String, Object> getOrderDetail(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = orderService.batchQueryExternal(id);
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("jdkVersion", "1.8");
        return result;
    }
}
