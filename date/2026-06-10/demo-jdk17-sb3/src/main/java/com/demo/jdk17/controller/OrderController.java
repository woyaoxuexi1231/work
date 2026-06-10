package com.demo.jdk17.controller;

import com.demo.jdk17.dto.OrderDTO;
import com.demo.jdk17.dto.OrderStatus;
import com.demo.jdk17.service.OrderService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * <h3>JDK 17 / Spring Boot 3 Controller</h3>
 *
 * <p>核心变化：javax → jakarta 命名空间</p>
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

    // JDK 17：内联 Record 做请求参数（对比 JDK 8 需要单独的 class）
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

    /** 改进1：instanceof 模式匹配（对比 JDK 8 两步走强转） */
    @GetMapping("/describe")
    public String describe(@RequestParam(defaultValue = "hello") String input) {
        return orderService.describeObject(input);
    }

    /** 改进2：switch 表达式 + sealed 穷举（对比 JDK 8 switch 字符串） */
    @GetMapping("/status-desc")
    public String statusDescription(@RequestParam(defaultValue = "PENDING") String statusType) {
        OrderStatus status = switch (statusType.toUpperCase()) {
            case "PAID" -> new OrderStatus.Paid(new BigDecimal("99.99"));
            case "SHIPPED" -> new OrderStatus.Shipped("SF1234567890");
            case "CANCELLED" -> new OrderStatus.Cancelled("用户主动取消");
            default -> new OrderStatus.Pending();
        };
        return orderService.getOrderStatusDescription(status);
    }

    /** 改进3：Text Blocks 订单报告 */
    @GetMapping("/{id}/report")
    public String orderReport(@PathVariable Long id) {
        return orderService.getOrder(id)
                .map(orderService::generateOrderReport)
                .orElse("订单不存在");
    }

    /** 并发：仍用 CompletableFuture（虚线程要 JDK 21） */
    @GetMapping("/{id}/detail")
    public Map<String, Object> getOrderDetail(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = orderService.batchQueryExternal(id);
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("jdkVersion", "17");
        return result;
    }
}
