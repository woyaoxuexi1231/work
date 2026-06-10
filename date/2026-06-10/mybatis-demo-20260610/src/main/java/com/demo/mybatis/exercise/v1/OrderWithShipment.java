package com.demo.mybatis.exercise.v1;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author hulei
 * @since 2026/6/10 16:21
 */

@Data
public class OrderWithShipment {
    private Long orderId;
    private String productName;
    private BigDecimal amount;
    private Shipment shipment;
}
