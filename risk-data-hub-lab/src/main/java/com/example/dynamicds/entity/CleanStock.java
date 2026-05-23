package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.sync.CleanRecordContext;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("clean_stock")
public class CleanStock {
    @TableId(type = IdType.INPUT)
    private Long globalId;
    private String sourceSystem;
    private String sourceType;
    private Long sourceRowId;
    private String stockCode;
    private String exchangeCode;
    private String marketDay;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volumeQty;
    private BigDecimal turnoverAmount;
    private String cleanBatch;
    private String createdAt;

    public static CleanStock create(CleanRecordContext context,
                                    String stockCode,
                                    String exchangeCode,
                                    String marketDay,
                                    BigDecimal openPrice,
                                    BigDecimal highPrice,
                                    BigDecimal lowPrice,
                                    BigDecimal closePrice,
                                    Long volumeQty,
                                    BigDecimal turnoverAmount) {
        CleanStock stock = new CleanStock();
        stock.setGlobalId(context.globalId());
        stock.setSourceSystem(context.sourceSystem());
        stock.setSourceType(context.sourceType());
        stock.setSourceRowId(context.sourceRowId());
        stock.setStockCode(stockCode);
        stock.setExchangeCode(exchangeCode);
        stock.setMarketDay(marketDay);
        stock.setOpenPrice(openPrice);
        stock.setHighPrice(highPrice);
        stock.setLowPrice(lowPrice);
        stock.setClosePrice(closePrice);
        stock.setVolumeQty(volumeQty);
        stock.setTurnoverAmount(turnoverAmount);
        stock.setCleanBatch(context.cleanBatch());
        stock.setCreatedAt(context.createdAt());
        return stock;
    }
}
