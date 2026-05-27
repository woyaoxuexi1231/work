package com.example.dubbo.demo.api.service;

import com.example.dubbo.demo.api.model.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * <h3>订单服务接口（第二个 Dubbo 服务契约）</h3>
 *
 * <p>
 * 设计此接口是为了演示 <b>Provider 同时暴露多个服务</b> 的场景。
 * 在 Dubbo 中，一个 Provider 进程可以同时暴露多个服务接口，
 * 每个接口独立注册到注册中心，Consumer 按需引用。
 * </p>
 *
 * <h4>多服务暴露示意图</h4>
 * <pre>
 * Provider JVM 进程
 * ┌─────────────────────────────────────┐
 * │  dubbo://:20880/UserService         │ ← 协议端口 20880
 * │  dubbo://:20880/OrderService         │ ← 同一端口，不同服务路径
 * │  dubbo://:20881/AnotherService       │ ← 也可使用不同端口（多协议）
 * └─────────────────────────────────────┘
 * </pre>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see com.example.dubbo.demo.api.model.Order
 * @see UserService
 */
public interface OrderService {

    /**
     * 根据订单 ID 查询订单。
     *
     * @param orderId 订单 ID
     * @return 订单对象；未找到返回 {@code null}
     */
    Order getOrderById(Long orderId);

    /**
     * 查询某个用户的所有订单。
     *
     * @param userId 用户 ID
     * @return 订单列表
     */
    List<Order> listOrdersByUserId(Long userId);

    /**
     * 创建订单。
     *
     * @param order 订单对象（orderId 由服务端生成）
     * @return 生成的订单 ID
     */
    Long createOrder(Order order);

    /**
     * 计算指定用户的订单总金额。
     * <p>
     * 演示 Dubbo 对 {@link BigDecimal} 等复杂数值类型的序列化。
     * </p>
     *
     * @param userId 用户 ID
     * @return 该用户的订单金额合计
     */
    BigDecimal getTotalAmountByUserId(Long userId);
}
