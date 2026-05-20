package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("clean_position")
public class CleanPosition {
    @TableId(type = IdType.INPUT)
    private Long globalId;
    private String sourceSystem;
    private String sourceType;
    private Long sourceRowId;
    private String accountName;
    private String stockCode;
    private Long holdingQty;
    private Long availableQty;
    private BigDecimal costPrice;
    private BigDecimal marketValue;
    private String statDay;
    private String cleanBatch;
    private String createdAt;
}