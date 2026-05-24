package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.bootstrap.MarketSeedSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
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

    public static OmsTradeOrder fromSeed(Long id, MarketSeedSnapshot seed,
                                         String investorName, String sideCode,
                                         String tradeStatus, int repeat) {
        long qty = seed.tradeQty(0, repeat);
        BigDecimal price = seed.tradePrice(repeat);
        OmsTradeOrder order = new OmsTradeOrder();
        order.setId(id);
        order.setOrderNo("OMS-" + seed.symbol() + "-" + String.format("%05d", repeat) + "-" + repeat);
        order.setStockCode(seed.symbol());
        order.setInvestorName(investorName);
        order.setSideCode(sideCode);
        order.setTradeQty(qty);
        order.setTradePrice(price);
        order.setOrderAmount(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        order.setTradeStatus(tradeStatus);
        order.setTradeTime(seed.tradeTime());
        order.setSyncFlag(0);
        return order;
    }
}
