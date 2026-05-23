package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.bootstrap.MarketSeedSnapshot;
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

    public static BrokerStockQuote fromSeed(Long id, MarketSeedSnapshot seed) {
        long volume = seed.volume(17);
        BrokerStockQuote quote = new BrokerStockQuote();
        quote.setId(id);
        quote.setQuoteCode(seed.symbol() + "-" + seed.tradeDay());
        quote.setSecuCode(seed.symbol());
        quote.setTradeDay(seed.tradeDay());
        quote.setExchangeName(seed.exchangeOrDefault());
        quote.setOpenPx(seed.openPrice());
        quote.setHighPx(seed.highPrice());
        quote.setLowPx(seed.lowPrice());
        quote.setClosePx(seed.closePrice());
        quote.setVolNum(volume);
        quote.setTurnoverAmt(seed.turnover(volume));
        quote.setSyncFlag(0);
        return quote;
    }
}
