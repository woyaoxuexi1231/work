package com.demo.jdk21.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <h3>订单 DTO —— JDK 21 Record 写法（与 JDK 17 相同）</h3>
 *
 * <p>Record 在 JDK 16 正式引入，JDK 17/21 都可以用。
 * JDK 21 的 Record Patterns（记录模式）在 Service 中展示。</p>
 */
public record OrderDTO(
        Long id,
        String orderNo,
        BigDecimal amount,
        OrderStatus status,
        LocalDateTime createTime
) {
    // 紧凑构造器：设置默认值
    public OrderDTO {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
