package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.bootstrap.MarketSeedSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@TableName("broker_position_balance")
public class BrokerPositionBalance {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String clientFullName;
    private String secuCode;
    private Long currentVolume;
    private Long enableVolume;
    private BigDecimal costPx;
    private BigDecimal marketAmt;
    private String bizDate;
    private Integer syncFlag;

    public static BrokerPositionBalance fromSeed(Long id, MarketSeedSnapshot seed, String clientFullName) {
        long qty = seed.positionQty(5);
        BigDecimal price = seed.tradePrice(2);
        BrokerPositionBalance balance = new BrokerPositionBalance();
        balance.setId(id);
        balance.setClientFullName(clientFullName);
        balance.setSecuCode(seed.symbol());
        balance.setCurrentVolume(qty);
        balance.setEnableVolume(Math.max(100L, qty - 200L));
        balance.setCostPx(price);
        balance.setMarketAmt(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        balance.setBizDate(seed.tradeDay());
        balance.setSyncFlag(0);
        return balance;
    }
}
