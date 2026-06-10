package com.demo.jdk17.controller;

import com.demo.jdk17.dto.OrderDTO;
import com.demo.jdk17.dto.OrderStatus;
import com.demo.jdk17.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * <h3>JDK 17 / Spring Boot 3 Controller</h3>
 *
 * <p>核心变化：javax → jakarta 命名空间</p>
 */
@RestController
@RequestMapping("/api/demo")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 改进1：instanceof 模式匹配（对比 JDK 8 两步走强转） */
    @GetMapping("/describe")
    public String describe(@RequestParam(defaultValue = "hello") String input) {
        if ("order".equals(input)) {
            return orderService.describeObject(
                    new OrderDTO(1L, "ORD-001", new BigDecimal("99.9"),
                            new OrderStatus.Paid(new BigDecimal("99.9")), LocalDateTime.now()));
        }
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
    @GetMapping("/report")
    public String report() {
        var order = new OrderDTO(1L, "ORD-001", new BigDecimal("188.88"),
                new OrderStatus.Paid(new BigDecimal("188.88")), LocalDateTime.now());
        return orderService.generateOrderReport(order);
    }

    /** 并发：仍用 CompletableFuture（虚线程要 JDK 21） */
    @GetMapping("/concurrency")
    public Map<String, Object> concurrency(@RequestParam(defaultValue = "1") Long id) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = orderService.batchQueryExternal(id);
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("jdkVersion", "17");
        return result;
    }
}
