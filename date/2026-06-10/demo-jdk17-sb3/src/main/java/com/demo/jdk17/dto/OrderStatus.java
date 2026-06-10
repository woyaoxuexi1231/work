package com.demo.jdk17.dto;

import java.math.BigDecimal;

/**
 * <h3>订单状态 —— JDK 17 Sealed Interface 写法</h3>
 *
 * <p><strong>对比 JDK 8 的枚举（demo-jdk8-sb2 中的 OrderStatus.java）：</strong></p>
 * <pre>
 * // JDK 8：传统枚举，每个值结构相同，不能携带不同数据
 * public enum OrderStatus {
 *     PENDING("待支付"),
 *     PAID("已支付"),
 *     SHIPPED("已发货"),
 *     CANCELLED("已取消");
 * }
 * </pre>
 *
 * <p><strong>JDK 17 Sealed Interface + Record 的优势：</strong></p>
 * <ul>
 *   <li>每种状态可以携带完全不同的数据（Paid 有金额，Shipped 有快递单号）</li>
 *   <li>permits 限定实现类，编译器知道所有可能的子类</li>
 *   <li>switch sealed 类型时编译器强制穷举，漏写直接报错！</li>
 *   <li>配合 switch 表达式，代码量减少 80%</li>
 * </ul>
 */
public sealed interface OrderStatus permits OrderStatus.Pending, OrderStatus.Paid, OrderStatus.Shipped, OrderStatus.Cancelled {

    /**
     * 显示文本（每种状态自己实现）
     */
    String display();

    // ---- 四种状态用 Record 实现，嵌套在接口内部，每种可以携带不同的数据 ----

    /** 待支付 */
    record Pending() implements OrderStatus {
        @Override
        public String display() {
            return "待支付";
        }
    }

    /** 已支付 —— 注意：可以携带支付金额！枚举做不到 */
    record Paid(BigDecimal paidAmount) implements OrderStatus {
        @Override
        public String display() {
            return "已支付 " + paidAmount + " 元";
        }
    }

    /** 已发货 —— 可以携带快递单号！枚举做不到 */
    record Shipped(String trackingNo) implements OrderStatus {
        @Override
        public String display() {
            return "已发货，单号: " + trackingNo;
        }
    }

    /** 已取消 —— 可以携带取消原因！枚举做不到 */
    record Cancelled(String reason) implements OrderStatus {
        @Override
        public String display() {
            return "已取消: " + reason;
        }
    }
}
