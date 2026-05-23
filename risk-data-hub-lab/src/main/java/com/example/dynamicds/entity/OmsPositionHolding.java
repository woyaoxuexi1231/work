package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.bootstrap.MarketSeedSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
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

    public static OmsPositionHolding fromSeed(Long id, MarketSeedSnapshot seed, String investorName) {
        long qty = seed.positionQty(0);
        BigDecimal price = seed.tradePrice(1);
        OmsPositionHolding holding = new OmsPositionHolding();
        holding.setId(id);
        holding.setInvestorName(investorName);
        holding.setStockCode(seed.symbol());
        holding.setHoldingQty(qty);
        holding.setAvailableQty(Math.max(100L, qty - 100L));
        holding.setCostPrice(price);
        holding.setMarketValue(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        holding.setStatDay(seed.tradeDay());
        holding.setSyncFlag(0);
        return holding;
    }
}
