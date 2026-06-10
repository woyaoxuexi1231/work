package com.demo.jdk17.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <h3>订单 DTO —— JDK 17 Record 写法</h3>
 *
 * <p>对比 JDK 8 POJO + Lombok：Record 一行搞定，不需要 @Data @Builder</p>
 * <p>对比 JDK 8 String status：这里用 sealed interface，类型安全</p>
 */
public record OrderDTO(
        Long id,
        String orderNo,
        BigDecimal amount,
        OrderStatus status,
        LocalDateTime createTime
) {
    // Record 紧凑构造器
    public OrderDTO {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
