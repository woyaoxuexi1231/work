package com.example.dynamicds.bootstrap;

import java.math.BigDecimal;

/**
 * 市场种子数据快照 — 用于 DemoDataSeeder 生成模拟行情数据
 */
public class MarketSeedSnapshot {
    private final String symbol;
    private final String exchangeCode;
    private final String tradeDay;
    private final BigDecimal openPrice;
    private final BigDecimal highPrice;
    private final BigDecimal lowPrice;
    private final BigDecimal closePrice;
    private final long volumeQty;

    public MarketSeedSnapshot(String symbol, String exchangeCode, String tradeDay,
                               BigDecimal openPrice, BigDecimal highPrice,
                               BigDecimal lowPrice, BigDecimal closePrice, long volumeQty) {
        this.symbol = symbol;
        this.exchangeCode = exchangeCode;
        this.tradeDay = tradeDay;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volumeQty = volumeQty;
    }

    public String symbol() { return symbol; }
    public String exchangeCode() { return exchangeCode; }
    public String exchangeOrDefault() { return exchangeCode != null ? exchangeCode : "XSHG"; }
    public String tradeDay() { return tradeDay; }
    public BigDecimal openPrice() { return openPrice; }
    public BigDecimal highPrice() { return highPrice; }
    public BigDecimal lowPrice() { return lowPrice; }
    public BigDecimal closePrice() { return closePrice; }
    public long volume(long defaultVal) { return volumeQty > 0 ? volumeQty : defaultVal; }
    public long volumeQty() { return volumeQty; }
    public BigDecimal turnover(long defaultVal) { return volumeQty > 0 ? closePrice.multiply(BigDecimal.valueOf(volumeQty)) : BigDecimal.valueOf(defaultVal); }
    public long tradeQty(int idx, int repeat) { return (volumeQty / Math.max(repeat + 1, 1)) + idx * 100; }
    public BigDecimal tradePrice(int repeat) { return closePrice.add(BigDecimal.valueOf(repeat * 0.01)); }
    public String tradeTime() { return tradeDay + " 15:00:00"; }
    public BigDecimal turnoverAmount() { return closePrice.multiply(BigDecimal.valueOf(Math.max(volumeQty, 1))); }
}
