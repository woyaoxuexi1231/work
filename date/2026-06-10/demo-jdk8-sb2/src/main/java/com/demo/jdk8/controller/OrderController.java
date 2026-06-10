package com.demo.jdk8.controller;

import com.demo.jdk8.dto.OrderDTO;
import com.demo.jdk8.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * <h3>JDK 8 / Spring Boot 2 Controller</h3>
 *
 * <p>注意 javax 命名空间 → Spring Boot 3 变成 jakarta</p>
 */
@RestController
@RequestMapping("/api/demo")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 痛点1：instanceof 两步走 → JDK 17 模式匹配一步到位 */
    @GetMapping("/describe")
    public String describe(@RequestParam(defaultValue = "hello") String input) {
        // 用 input 构造一个 OrderDTO 来演示 instanceof
        if ("order".equals(input)) {
            return orderService.describeObject(
                    OrderDTO.builder().id(1L).orderNo("ORD-001")
                            .amount(new BigDecimal("99.9")).build());
        }
        return orderService.describeObject(input);
    }

    /** 痛点2：switch 字符串 → JDK 17 switch 表达式 + sealed 穷举 */
    @GetMapping("/status-desc")
    public String statusDescription(@RequestParam(defaultValue = "PENDING") String status) {
        return orderService.getOrderStatusDescription(status);
    }

    /** 痛点3：CompletableFuture → JDK 21 虚线程 */
    @GetMapping("/concurrency")
    public Map<String, Object> concurrency(@RequestParam(defaultValue = "1") Long id) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = orderService.batchQueryExternal(id);
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("jdkVersion", "1.8");
        return result;
    }
}
