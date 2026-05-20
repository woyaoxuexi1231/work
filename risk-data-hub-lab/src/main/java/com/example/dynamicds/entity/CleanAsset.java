package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("clean_asset")
public class CleanAsset {
    @TableId(type = IdType.INPUT)
    private Long globalId;
    private String sourceSystem;
    private String sourceType;
    private Long sourceRowId;
    private String accountName;
    private String accountNo;
    private BigDecimal cashBalance;
    private BigDecimal frozenBalance;
    private BigDecimal totalAsset;
    private String statDay;
    private String cleanBatch;
    private String createdAt;
}