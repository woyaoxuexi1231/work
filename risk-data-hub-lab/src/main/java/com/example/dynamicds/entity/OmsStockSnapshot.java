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
@TableName("oms_stock_snapshot")
public class OmsStockSnapshot {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String symbol;
    private String exchangeCode;
    private String marketDay;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volumeQty;
    private BigDecimal turnoverAmount;
    private Integer syncFlag;
}
