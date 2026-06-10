package com.demo.jdk21.controller;

import com.demo.jdk21.dto.OrderDTO;
import com.demo.jdk21.dto.OrderStatus;
import com.demo.jdk21.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * <h3>JDK 21 / Spring Boot 3 Controller</h3>
 *
 * <p>spring.threads.virtual.enabled=true → Tomcat 自动用虚线程</p>
 */
@RestController
@RequestMapping("/api/demo")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 🔥 核心：虚线程并发（对比 JDK 8/17 的 CompletableFuture） */
    @GetMapping("/concurrency")
    public Map<String, Object> concurrency(@RequestParam(defaultValue = "1") Long id) {
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
                new OrderDTO(1L, "ORD-001", new BigDecimal("88.88"),
                        new OrderStatus.Pending(), LocalDateTime.now()));
    }

    /** Sequenced Collections（JDK 21 新增 getFirst/getLast/reversed） */
    @GetMapping("/sequenced")
    public Map<String, Object> sequencedDemo() {
        return orderService.sequencedCollectionDemo();
    }
}
