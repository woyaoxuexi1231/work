package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.bootstrap.MarketSeedSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@TableName("broker_trade_deal")
public class BrokerTradeDeal {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String dealCode;
    private String secuCode;
    private String clientFullName;
    private String bsFlag;
    private Long dealVolume;
    private BigDecimal dealPrice;
    private BigDecimal turnoverAmount;
    private String statusMark;
    private String dealAt;
    private Integer syncFlag;

    public static BrokerTradeDeal fromSeed(Long id, MarketSeedSnapshot seed,
                                           String clientFullName, String bsFlag,
                                           String statusMark, int repeat) {
        long qty = seed.tradeQty(11, repeat + 1);
        BigDecimal price = seed.tradePrice(repeat + 1);
        BrokerTradeDeal deal = new BrokerTradeDeal();
        deal.setId(id);
        deal.setDealCode("BRK-" + seed.symbol() + "-" + String.format("%05d", repeat) + "-" + repeat);
        deal.setSecuCode(seed.symbol());
        deal.setClientFullName(clientFullName);
        deal.setBsFlag(bsFlag);
        deal.setDealVolume(qty);
        deal.setDealPrice(price);
        deal.setTurnoverAmount(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        deal.setStatusMark(statusMark);
        deal.setDealAt(seed.tradeTime());
        deal.setSyncFlag(0);
        return deal;
    }
}
