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
@TableName("broker_stock_quote")
public class BrokerStockQuote {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String quoteCode;
    private String secuCode;
    private String tradeDay;
    private String exchangeName;
    private BigDecimal openPx;
    private BigDecimal highPx;
    private BigDecimal lowPx;
    private BigDecimal closePx;
    private Long volNum;
    private BigDecimal turnoverAmt;
    private Integer syncFlag;
}
