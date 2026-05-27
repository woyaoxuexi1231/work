package com.example.dubbo.demo.api.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.StringJoiner;

/**
 * <h3>订单领域模型</h3>
 * <p>
 * 与 {@link User} 相同，作为跨 JVM 传输的 POJO，必须实现 {@link Serializable}。
 * 设计两个不同模型是为了演示"多服务接口"场景——Provider 可同时暴露多个服务。
 * </p>
 *
 * <p><b>Dubbo 知识点映射：</b></p>
 * <ul>
 *   <li>§1 核心概念 — Provider 可暴露多个服务接口</li>
 *   <li>§9 高级特性 — 复杂参数类型的序列化行为</li>
 * </ul>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see User
 * @see Serializable
 */
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 下单用户 ID */
    private Long userId;

    /** 商品名称 */
    private String productName;

    /** 订单金额 */
    private BigDecimal amount;

    /** 下单时间 */
    private Date createTime;

    // ==================== 构造方法 ====================

    /** 无参构造（序列化框架要求） */
    public Order() {}

    /**
     * 便捷构造方法。
     *
     * @param orderId     订单 ID
     * @param userId      用户 ID
     * @param productName 商品名称
     * @param amount      金额
     * @param createTime  下单时间
     */
    public Order(Long orderId, Long userId, String productName,
                 BigDecimal amount, Date createTime) {
        this.orderId = orderId;
        this.userId = userId;
        this.productName = productName;
        this.amount = amount;
        this.createTime = createTime;
    }

    // ==================== Getter / Setter ====================

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Order.class.getSimpleName() + "[", "]")
                .add("orderId=" + orderId)
                .add("userId=" + userId)
                .add("productName='" + productName + "'")
                .add("amount=" + amount)
                .add("createTime=" + createTime)
                .toString();
    }
}
