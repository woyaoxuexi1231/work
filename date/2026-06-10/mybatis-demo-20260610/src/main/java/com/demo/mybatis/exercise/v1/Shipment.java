package com.demo.mybatis.exercise.v1;

import lombok.Data;

@Data
public class Shipment {
    private Long shipmentId;
    private String trackingNumber;
    private String status;
}