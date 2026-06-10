package com.demo.jdk8.service;

import com.demo.jdk8.dto.OrderDTO;
import com.demo.jdk8.dto.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <h3>JDK 8 经典写法 —— 展示当时的编码痛点</h3>
 *
 * <p>每个方法都对应 JDK 17/21 中的改进版本，方便对比。</p>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final List<OrderDTO> orders = Collections.synchronizedList(new ArrayList<>());
    private long idCounter = 0;

    public OrderDTO createOrder(String orderNo, BigDecimal amount) {
        OrderDTO order = OrderDTO.builder()
                .id(++idCounter)
                .orderNo(orderNo)
                .amount(amount)
                .status(OrderStatus.PENDING.name())
                .createTime(LocalDateTime.now())
                .build();
        orders.add(order);
        log.info("✅ 创建订单: {}", order);
        return order;
    }

    public Optional<OrderDTO> getOrder(Long id) {
        return orders.stream().filter(o -> o.getId().equals(id)).findFirst();
    }

    public List<OrderDTO> getAllOrders() {
        return new ArrayList<>(orders);
    }

    // ========================================================================
    // 痛点 1：instanceof 需要先判断再强转
    // JDK 17 改进 → instanceof 模式匹配，判断+绑定一步完成
    // ========================================================================

    public String describeObject(Object obj) {
        // JDK 8：两步走（判断 + 强转）
        if (obj instanceof OrderDTO) {
            OrderDTO order = (OrderDTO) obj; // 必须手动强转
            return "订单: " + order.getOrderNo() + ", 金额: " + order.getAmount();
        }
        if (obj instanceof String) {
            String str = (String) obj; // 又一次手动强转
            return "字符串: " + str;
        }
        return "未知类型: " + obj.getClass().getSimpleName();
    }

    // ========================================================================
    // 痛点 2：switch 只能用字符串/枚举值，不能模式匹配
    // JDK 17 改进 → switch 表达式 + sealed 类型，编译器强制穷举
    // ========================================================================

    public String getOrderStatusDescription(String status) {
        // JDK 8：switch 字符串，漏写 case 编译不报错
        switch (status) {
            case "PENDING":   return "待支付订单";
            case "PAID":      return "已支付订单";
            case "SHIPPED":   return "已发货订单";
            case "CANCELLED": return "已取消订单";
            default:          return "未知状态: " + status; // 新增状态但忘加case → 静默返回"未知"
        }
    }

    // ========================================================================
    // 痛点 3：并发调用外部 API —— CompletableFuture 嵌套
    // JDK 21 改进 → 虚线程，写同步代码获得异步性能
    // ========================================================================

    public Map<String, Object> batchQueryExternal(Long orderId) {
        // JDK 8：CompletableFuture + 固定线程池（每个任务占用一个平台线程 ~1MB）
        ExecutorService executor = Executors.newFixedThreadPool(3);

        CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> {
            log.info("📡 调用订单详情 API（线程: {}）", Thread.currentThread().getName());
            simulateSlowApi();
            return "order-detail-" + orderId;
        }, executor);

        CompletableFuture<String> payFuture = CompletableFuture.supplyAsync(() -> {
            log.info("📡 调用支付信息 API（线程: {}）", Thread.currentThread().getName());
            simulateSlowApi();
            return "payment-info-" + orderId;
        }, executor);

        CompletableFuture<String> logisticsFuture = CompletableFuture.supplyAsync(() -> {
            log.info("📡 调用物流信息 API（线程: {}）", Thread.currentThread().getName());
            simulateSlowApi();
            return "logistics-info-" + orderId;
        }, executor);

        // 等待全部完成
        CompletableFuture.allOf(orderFuture, payFuture, logisticsFuture).join();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", orderFuture.join());
        result.put("payment", payFuture.join());
        result.put("logistics", logisticsFuture.join());
        result.put("threadType", "platform-thread");
        result.put("codeStyle", "CompletableFuture");

        executor.shutdown();
        return result;
    }

    private void simulateSlowApi() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
