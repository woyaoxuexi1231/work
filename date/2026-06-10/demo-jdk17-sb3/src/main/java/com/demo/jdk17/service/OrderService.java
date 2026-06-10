package com.demo.jdk17.service;

import com.demo.jdk17.dto.OrderDTO;
import com.demo.jdk17.dto.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * <h3>JDK 17 现代写法 —— 对比 JDK 8 的痛点改进</h3>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // ========================================================================
    // 改进 1：instanceof 模式匹配（对比 JDK 8 的两步走强转）
    // ========================================================================

    public String describeObject(Object obj) {
        // JDK 17：判断 + 绑定一步完成，无需强转！
        if (obj instanceof OrderDTO order) {
            return "订单: %s, 金额: %s, 状态: %s".formatted(order.orderNo(), order.amount(), order.status().display());
        }
        if (obj instanceof String s) {
            return "字符串(长度 %d): %s".formatted(s.length(), s);
        }
        return "未知类型: " + obj.getClass().getSimpleName();
    }

    // ========================================================================
    // 改进 2：Switch 表达式 + sealed 类型模式匹配（对比 JDK 8 switch 字符串）
    // ========================================================================

    public String getOrderStatusDescription(OrderStatus status) {
        // JDK 17：switch sealed 类型，编译器强制穷举，不需要 default！
        return switch (status) {
            case OrderStatus.Pending p   -> "待支付订单";
            case OrderStatus.Paid p      -> "已支付 %s 元".formatted(p.paidAmount());
            case OrderStatus.Shipped s   -> "已发货，快递单号: " + s.trackingNo();
            case OrderStatus.Cancelled c -> "已取消: " + c.reason();
        };
    }

    // ========================================================================
    // 改进 3：Text Blocks（对比 JDK 8 字符串拼接 \n + 号）
    // ========================================================================

    public String generateOrderReport(OrderDTO order) {
        // JDK 17：Text Blocks，多行字符串清晰易读
        return """
                === 订单报告 ===
                订单号: %s
                金额: %s
                状态: %s
                =================
                """.formatted(order.orderNo(), order.amount(), order.status().display());
    }

    // ========================================================================
    // 并发调用：JDK 17 仍用 CompletableFuture（虚线程要 JDK 21）
    // ========================================================================

    public Map<String, Object> batchQueryExternal(Long orderId) {
        // JDK 17：和 JDK 8 一样 CompletableFuture，但可以用 var 简化
        var executor = Executors.newFixedThreadPool(3);

        var orderFuture = CompletableFuture.supplyAsync(() -> {
            log.info("📡 调用订单详情 API（线程: {}）", Thread.currentThread().getName());
            simulateSlowApi();
            return "order-detail-" + orderId;
        }, executor);

        var payFuture = CompletableFuture.supplyAsync(() -> {
            log.info("📡 调用支付信息 API（线程: {}）", Thread.currentThread().getName());
            simulateSlowApi();
            return "payment-info-" + orderId;
        }, executor);

        var logisticsFuture = CompletableFuture.supplyAsync(() -> {
            log.info("📡 调用物流信息 API（线程: {}）", Thread.currentThread().getName());
            simulateSlowApi();
            return "logistics-info-" + orderId;
        }, executor);

        CompletableFuture.allOf(orderFuture, payFuture, logisticsFuture).join();

        var result = new LinkedHashMap<String, Object>();
        result.put("order", orderFuture.join());
        result.put("payment", payFuture.join());
        result.put("logistics", logisticsFuture.join());
        result.put("threadType", "platform-thread（每个 ~1MB）");
        result.put("codeStyle", "CompletableFuture（虚线程要 JDK 21）");

        executor.shutdown();
        return result;
    }

    private void simulateSlowApi() {
        try { Thread.sleep(2000); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new RuntimeException(e);
        }
    }
}
