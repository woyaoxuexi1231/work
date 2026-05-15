package com.example.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Order {
    private String orderId;
    private double amount;
    private long timestamp;
    private Integer retryCount;
}