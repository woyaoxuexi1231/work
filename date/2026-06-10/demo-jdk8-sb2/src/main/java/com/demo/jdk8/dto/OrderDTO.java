package com.demo.jdk8.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <h3>订单 DTO —— JDK 8 经典写法：传统 POJO + Lombok</h3>
 *
 * <p><strong>JDK 8 时代的做法：</strong></p>
 * <ul>
 *   <li>手写 getter/setter/toString/equals/hashCode，或者用 Lombok 的 @Data 注解自动生成</li>
 *   <li>必须用 @NoArgsConstructor + @AllArgsConstructor + @Builder 来支持各种创建方式</li>
 *   <li>字段全部可修改（非 final），存在被意外修改的风险</li>
 *   <li>状态字段用 String 类型，无类型安全（编译期无法检查非法状态）</li>
 * </ul>
 *
 * <p><strong>对比 JDK 17 的 Record 写法（一行搞定）：</strong></p>
 * <pre>
 * // JDK 17 Record：不可变、自带 equals/hashCode/toString、无需 Lombok
 * public record OrderDTO(
 *     Long id,
 *     {@literal @}NotBlank String orderNo,
 *     {@literal @}NotNull {@literal @}Positive BigDecimal amount,
 *     OrderStatus status,      // sealed interface，类型安全！
 *     LocalDateTime createTime
 * ) {}
 * </pre>
 *
 * <p><strong>差异总结：</strong></p>
 * <table>
 *   <tr><th>特性</th><th>JDK 8 POJO</th><th>JDK 17 Record</th></tr>
 *   <tr><td>代码量</td><td>~40 行（含注解）</td><td>1 行</td></tr>
 *   <tr><td>不可变性</td><td>需要手动 final</td><td>天生不可变</td></tr>
 *   <tr><td>Lombok 依赖</td><td>必须</td><td>不需要</td></tr>
 *   <tr><td>equals/hashCode</td><td>自动生成但可能不一致</td><td>基于所有字段</td></tr>
 *   <tr><td>解构</td><td>不支持</td><td>Record Pattern (JDK 21)</td></tr>
 * </table>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private Long id;

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "金额不能为空")
    @Positive(message = "金额必须为正数")
    private BigDecimal amount;

    /**
     * JDK 8 时代的做法：状态用 String 类型
     * 问题：调用方可以传入任意字符串，编译期无法检查
     *
     * 对比 JDK 17：用 sealed interface OrderStatus，编译器强制穷举所有状态
     */
    private String status;

    private LocalDateTime createTime;
}
