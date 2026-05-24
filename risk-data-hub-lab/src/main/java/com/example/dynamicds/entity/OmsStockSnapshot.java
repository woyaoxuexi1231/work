package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.bootstrap.MarketSeedSnapshot;
import lombok.Data;

import java.math.BigDecimal;

@Data
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

    public static OmsStockSnapshot fromSeed(Long id, MarketSeedSnapshot seed) {
        long volume = seed.volume(0);
        OmsStockSnapshot snapshot = new OmsStockSnapshot();
        snapshot.setId(id);
        snapshot.setSymbol(seed.symbol());
        snapshot.setExchangeCode(seed.exchangeOrDefault());
        snapshot.setMarketDay(seed.tradeDay());
        snapshot.setOpenPrice(seed.openPrice());
        snapshot.setHighPrice(seed.highPrice());
        snapshot.setLowPrice(seed.lowPrice());
        snapshot.setClosePrice(seed.closePrice());
        snapshot.setVolumeQty(volume);
        snapshot.setTurnoverAmount(seed.turnover(volume));
        snapshot.setSyncFlag(0);
        return snapshot;
    }
}
