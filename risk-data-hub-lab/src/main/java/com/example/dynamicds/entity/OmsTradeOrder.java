package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("oms_trade_order")
public class OmsTradeOrder {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String orderNo;
    private String stockCode;
    private String investorName;
    private String sideCode;
    private Long tradeQty;
    private BigDecimal tradePrice;
    private BigDecimal orderAmount;
    private String tradeStatus;
    private String tradeTime;
    private Integer syncFlag;
}
