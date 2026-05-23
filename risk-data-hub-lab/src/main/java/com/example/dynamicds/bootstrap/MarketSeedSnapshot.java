package com.example.dynamicds.bootstrap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MarketSeedSnapshot(
        String symbol,
        String exchange,
        LocalDate date,
        String open,
        String high,
        String low,
        String close,
        String volume
) {

    private static final DateTimeFormatter TRADE_DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Map<String, MarketSeedSnapshot> FALLBACK_MAP = new LinkedHashMap<>();

    static {
        FALLBACK_MAP.put("AAPL", new MarketSeedSnapshot("AAPL", "NASDAQ", LocalDate.of(2024, 12, 16), "150.00", "152.00", "149.00", "151.50", "10000000"));
        FALLBACK_MAP.put("GOOGL", new MarketSeedSnapshot("GOOGL", "NASDAQ", LocalDate.of(2024, 12, 16), "130.00", "132.00", "129.00", "131.50", "8000000"));
    }

    public static List<MarketSeedSnapshot> fallbackSeeds() {
        return new ArrayList<>(FALLBACK_MAP.values());
    }

    public BigDecimal openPrice() {
        return price(open);
    }

    public BigDecimal highPrice() {
        return price(high);
    }

    public BigDecimal lowPrice() {
        return price(low);
    }

    public BigDecimal closePrice() {
        return price(close);
    }

    public String exchangeOrDefault() {
        return exchange != null ? exchange : "SSE";
    }

    public String tradeDay() {
        return date.format(TRADE_DAY_FORMATTER);
    }

    public String tradeTime() {
        return date.format(TRADE_TIME_FORMATTER) + " 09:30:00";
    }

    public long volume(int offset) {
        try {
            return Long.parseLong(volume) + offset;
        } catch (Exception e) {
            return 1_000_000L + offset;
        }
    }

    public long tradeQty(int offset, int repeat) {
        return volume(offset) / (repeat + 1) / 100;
    }

    public long positionQty(int offset) {
        return volume(offset) / 10;
    }

    public BigDecimal tradePrice(int offset) {
        return closePrice().add(BigDecimal.valueOf(offset * 0.01d)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal turnover(long currentVolume) {
        return closePrice().multiply(BigDecimal.valueOf(currentVolume)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal price(String raw) {
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (Exception e) {
            return new BigDecimal("100.00");
        }
    }
}
