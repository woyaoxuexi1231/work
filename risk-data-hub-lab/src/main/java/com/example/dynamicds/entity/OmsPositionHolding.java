package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("oms_position_holding")
public class OmsPositionHolding {
    @TableId(type = IdType.INPUT)
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
