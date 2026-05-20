package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("clean_stock")
public class CleanStock {
    @TableId(type = IdType.INPUT)
    private Long globalId;
    private String sourceSystem;
    private String sourceType;
    private Long sourceRowId;
    private String stockCode;
    private String exchangeCode;
    private String marketDay;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volumeQty;
    private BigDecimal turnoverAmount;
    private String cleanBatch;
    private String createdAt;
}