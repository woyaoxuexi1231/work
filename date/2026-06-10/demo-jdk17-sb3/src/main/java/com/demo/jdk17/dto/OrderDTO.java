package com.demo.jdk17.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <h3>订单 DTO —— JDK 17 Record 写法</h3>
 *
 * <p><strong>对比 JDK 8 的 POJO + Lombok（demo-jdk8-sb2 中的 OrderDTO.java）：</strong></p>
 * <pre>
 * // JDK 8：~40 行代码，需要 Lombok 的 @Data @Builder @NoArgsConstructor @AllArgsConstructor
 * &#064;Data @Builder @NoArgsConstructor @AllArgsConstructor
 * public class OrderDTO {
 *     private Long id;
 *     private String orderNo;
 *     private BigDecimal amount;
 *     private String status;    // ← 字符串状态，无类型安全
 *     private LocalDateTime createTime;
 * }
 * </pre>
 *
 * <p><strong>JDK 17 Record 的优势：</strong></p>
 * <ul>
 *   <li>一行定义，自动生成构造器、访问器、equals、hashCode、toString</li>
 *   <li>天生不可变（所有字段 final），线程安全</li>
 *   <li>不需要 Lombok，零外部依赖</li>
 *   <li>状态用 sealed interface OrderStatus，类型安全</li>
 *   <li>JDK 21 还支持 Record Pattern 解构</li>
 * </ul>
 *
 * <p><strong>注意 jakarta 命名空间（Spring Boot 3 的变化）：</strong></p>
 * <pre>
 * // Spring Boot 2 (JDK 8): import javax.validation.constraints.*
 * // Spring Boot 3 (JDK 17): import jakarta.validation.constraints.*  ← 注意！
 * </pre>
 */
public record OrderDTO(
        Long id,

        @NotBlank(message = "订单号不能为空")
        String orderNo,

        @NotNull(message = "金额不能为空")
        @Positive(message = "金额必须为正数")
        BigDecimal amount,

        // 对比 JDK 8：这里用 sealed interface 而非 String，编译器强制穷举所有状态
        OrderStatus status,

        LocalDateTime createTime
) {
    // Record 可以有紧凑构造器（做校验/默认值）
    public OrderDTO {
        if (createTime == null) {
            createTime = LocalDateTime.now(); // 默认当前时间
        }
    }
}
