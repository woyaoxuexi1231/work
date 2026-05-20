package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("oms_position_holding")
public class OmsPositionHolding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String investorName;
    private String stockCode;
    private Long holdingQty;
    private Long availableQty;
    private BigDecimal costPrice;
    private BigDecimal marketValue;
    private String statDay;
    private Integer syncFlag;
}
