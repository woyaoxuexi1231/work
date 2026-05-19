package com.example.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author hulei
 * @since 2026/5/18 13:36
 */

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ProductV2 {
    private String productId;
    private BigDecimal price;
    private String currency;
    private BigDecimal discount;
}
