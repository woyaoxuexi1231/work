package com.demo.jdk8.dto;

/**
 * <h3>订单状态 —— JDK 8 经典写法：传统枚举</h3>
 *
 * <p><strong>JDK 8 时代的做法：</strong></p>
 * <ul>
 *   <li>用 enum 定义有限状态集合</li>
 *   <li>每个枚举值可以携带额外数据（如 displayName）</li>
 *   <li>但枚举是封闭的，不能根据不同状态携带不同的业务数据</li>
 *   <li>switch 枚举时如果漏写 case，编译器不会报错（除非有 default 抛异常）</li>
 * </ul>
 *
 * <p><strong>对比 JDK 17 的 Sealed Interface + Record 写法：</strong></p>
 * <pre>
 * // JDK 17：密封接口 + Record，每种状态可以携带不同的数据
 * public sealed interface OrderStatus permits Pending, Paid, Shipped, Cancelled {
 *     String display();
 * }
 * public record Pending() implements OrderStatus {
 *     public String display() { return "待支付"; }
 * }
 * public record Paid(BigDecimal paidAmount) implements OrderStatus {
 *     public String display() { return "已支付 " + paidAmount + " 元"; }
 * }
 * public record Shipped(String trackingNo) implements OrderStatus {
 *     public String display() { return "已发货，单号: " + trackingNo; }
 * }
 * public record Cancelled(String reason) implements OrderStatus {
 *     public String display() { return "已取消: " + reason; }
 * }
 * </pre>
 *
 * <p><strong>核心差异：</strong></p>
 * <ul>
 *   <li>枚举：每个值结构相同，不能携带不同的数据</li>
 *   <li>密封接口 + Record：每种状态可以有完全不同的字段（Paid 有金额，Shipped 有快递单号）</li>
 *   <li>switch sealed 类型时，编译器强制穷举，漏写一个就编译报错！</li>
 * </ul>
 */
public enum OrderStatus {

    PENDING("待支付"),
    PAID("已支付"),
    SHIPPED("已发货"),
    CANCELLED("已取消");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
