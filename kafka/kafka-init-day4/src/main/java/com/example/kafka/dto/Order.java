package com.example.kafka.dto;

import java.util.Objects;

public class Order {
    private String orderId;
    private double amount;
    private long timestamp;

    public Order() {}  // 无参构造反序列化必需

    public Order(String orderId, double amount, long timestamp) {
        this.orderId = orderId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    // getters/setters 省略，实际必须生成
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("Order{id='%s', amount=%.2f, ts=%d}", orderId, amount, timestamp);
    }
}