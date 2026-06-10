package com.demo.mybatis.entity;

import java.math.BigDecimal;

/**
 * 订单 — 一对多中的"多"方
 * 每个订单属于一个用户
 */
public class Order {
    private Long id;
    private Long userId;          // 数据库列 user_id → 自动映射为 userId
    private String productName;
    private BigDecimal amount;

    // ▼ association：多对一 — 订单所属的用户
    private User user;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    @Override
    public String toString() {
        return "Order{id=" + id + ", product='" + productName + "', amount=" + amount
                + (user != null ? ", user=" + user.getName() : "") + "}";
    }
}
