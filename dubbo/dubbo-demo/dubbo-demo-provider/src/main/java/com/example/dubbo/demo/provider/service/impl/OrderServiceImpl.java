package com.example.dubbo.demo.provider.service.impl;

import com.example.dubbo.demo.api.model.Order;
import com.example.dubbo.demo.api.service.OrderService;

import org.apache.dubbo.config.annotation.DubboService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * <h3>订单服务实现 — 第二个 Dubbo 服务暴露</h3>
 *
 * <p>
 * 此类与 {@link UserServiceImpl} 共同演示 <b>一个 Provider 进程暴露多个服务</b>
 * 的场景。Dubbo 支持在同一个端口上暴露多个服务接口，每个接口以不同的
 * <b>服务路径</b>（interface 全限定名）区分。
 * </p>
 *
 * <h4>多服务端口复用原理</h4>
 * <pre>
 * Provider 启动 Netty 服务端 → 监听 :20880
 *    ↓
 * 请求到达 → 解析 Dubbo 协议头 → 读取 service path
 *    ↓
 * 路由到对应的 ServiceConfig → 反射调用对应实现类方法
 * </pre>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see UserServiceImpl
 * @see OrderService
 */
@DubboService(
        version = "1.0.0",
        group = "demo",
        timeout = 3000,
        retries = 1,             // 订单服务重试 1 次（写操作重试需谨慎）
        weight = 100
)
@Component
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    /** 模拟订单存储 */
    private final Map<Long, Order> orderStore = new ConcurrentHashMap<>();

    /** ID 自增生成器 */
    private final AtomicLong idGenerator = new AtomicLong(0);

    // ==================== 初始化模拟数据 ====================
    {
        orderStore.put(101L, new Order(101L, 1L, "MacBook Pro", new BigDecimal("12999.00"), new Date()));
        orderStore.put(102L, new Order(102L, 1L, "iPhone 15",    new BigDecimal("6999.00"),  new Date()));
        orderStore.put(103L, new Order(103L, 2L, "AirPods Pro",  new BigDecimal("1999.00"),  new Date()));
        idGenerator.set(103L);
        log.info("OrderServiceImpl 初始化完成，已加载 {} 条模拟订单", orderStore.size());
    }

    // ==================== 接口实现 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public Order getOrderById(Long orderId) {
        log.info(">>> [Provider] 收到 RPC 请求: getOrderById({})", orderId);

        Order order = orderStore.get(orderId);

        if (order != null) {
            log.info("<<< [Provider] 返回: {}", order);
        } else {
            log.warn("<<< [Provider] 订单不存在: id={}", orderId);
        }

        return order;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> listOrdersByUserId(Long userId) {
        log.info(">>> [Provider] 收到 RPC 请求: listOrdersByUserId({})", userId);

        List<Order> result = orderStore.values().stream()
                .filter(o -> userId.equals(o.getUserId()))
                .collect(Collectors.toList());

        log.info("<<< [Provider] 返回 {} 条订单", result.size());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long createOrder(Order order) {
        log.info(">>> [Provider] 收到 RPC 请求: createOrder({})", order);

        long newId = idGenerator.incrementAndGet();
        order.setOrderId(newId);
        order.setCreateTime(new Date());

        orderStore.put(newId, order);

        log.info("<<< [Provider] 订单创建成功: id={}, product={}", newId, order.getProductName());
        return newId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * 演示 Dubbo 对 {@link BigDecimal}（高精度数值类型）的序列化支持。
     * 跨 JVM 传输时，BigDecimal 由 Hessian2 序列化为字符串格式，保证精度不丢失。
     * </p>
     */
    @Override
    public BigDecimal getTotalAmountByUserId(Long userId) {
        log.info(">>> [Provider] 收到 RPC 请求: getTotalAmountByUserId({})", userId);

        BigDecimal total = orderStore.values().stream()
                .filter(o -> userId.equals(o.getUserId()))
                .map(Order::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("<<< [Provider] 用户 {} 订单总额: {}", userId, total);
        return total;
    }
}
