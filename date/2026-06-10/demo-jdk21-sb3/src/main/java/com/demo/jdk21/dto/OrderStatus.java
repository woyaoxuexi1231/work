package com.demo.jdk21.dto;

import java.math.BigDecimal;

/**
 * <h3>订单状态 —— Sealed Interface（与 JDK 17 相同）</h3>
 */
public sealed interface OrderStatus permits OrderStatus.Pending, OrderStatus.Paid, OrderStatus.Shipped, OrderStatus.Cancelled {

    String display();

    record Pending() implements OrderStatus {
        @Override public String display() { return "待支付"; }
    }

    record Paid(BigDecimal paidAmount) implements OrderStatus {
        @Override public String display() { return "已支付 " + paidAmount + " 元"; }
    }

    record Shipped(String trackingNo) implements OrderStatus {
        @Override public String display() { return "已发货，单号: " + trackingNo; }
    }

    record Cancelled(String reason) implements OrderStatus {
        @Override public String display() { return "已取消: " + reason; }
    }
}
