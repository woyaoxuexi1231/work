package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@TableName("clean_trade")
@Data
public class CleanTrade {
    @TableId(type = IdType.INPUT)
    private Long globalId;
    private String sourceSystem;
    private String sourceType;
    private Long sourceRowId;
    private String vendorTradeNo;
    private String bizType;
    private String direction;
    private BigDecimal amount;
    private String statusName;
    private String counterpartyName;
    private String cleanMode;
    private String cleanBatch;
    private String tradeTime;
    private String createdAt;
}
