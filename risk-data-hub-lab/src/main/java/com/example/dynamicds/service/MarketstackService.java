package com.example.dynamicds.service;

import com.example.dynamicds.config.MarketstackProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Marketstack 行情数据服务 — 对接 Marketstack API 获取美股历史行情。
 *
 * 核心功能：
 * 1. 从 Marketstack EOD API 拉取股票日线数据
 * 2. 当 API 不可用时（网络/配额/配置缺失），自动切换到本地兜底数据生成
 * 3. 兜底数据使用伪随机算法生成合理的 OHLCV 数据
 *
 * 该服务只在平台初始化阶段（initDemoData）被调用一次。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketstackService {

    private final RestClient.Builder restClientBuilder;
    private final MarketstackProperties properties;

    public List<StockSnapshot> fetchBootstrapStocks() {
        if (!properties.isEnabled()) {
            log.warn("[Marketstack] 已禁用启动拉数");
            return Collections.emptyList();
        }
        if (properties.getSymbols() == null || properties.getSymbols().isEmpty()) {
            throw new IllegalStateException("marketstack.symbols 不能为空");
        }

        int pageSize = Math.max(1, Math.min(properties.getPageSize(), 1000));
        int maxRows = Math.max(pageSize, properties.getMaxRows());
        LocalDate dateTo = LocalDate.now();
        LocalDate dateFrom = dateTo.minusDays(Math.max(1, properties.getLookbackDays()));
        if (!StringUtils.hasText(properties.getAccessKey())) {
            log.warn("[Marketstack] 未配置 accessKey，直接使用本地兜底股票数据");
            return generateFallbackStocks(dateFrom, dateTo, maxRows);
        }

        String symbols = String.join(",", properties.getSymbols());
        log.info("[行情服务] 开始拉取 Marketstack 历史行情 symbols={}, 起始日期={}, 截止日期={}, 分页大小={}, 最大行数={}",
                symbols, dateFrom, dateTo, pageSize, maxRows);

        try {
            List<StockSnapshot> result = new ArrayList<>();
            int offset = 0;
            while (result.size() < maxRows) {
                final int currentOffset = offset;
                MarketstackResponse response = restClientBuilder
                        .baseUrl(properties.getBaseUrl())
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/eod")
                                .queryParam("access_key", properties.getAccessKey())
                                .queryParam("symbols", symbols)
                                .queryParam("date_from", dateFrom)
                                .queryParam("date_to", dateTo)
                                .queryParam("limit", pageSize)
                                .queryParam("offset", currentOffset)
                                .build())
                        .retrieve()
                        .body(MarketstackResponse.class);

                if (response == null || response.getData() == null || response.getData().isEmpty()) {
                    break;
                }
                result.addAll(response.getData());
                if (response.getPagination() == null || response.getData().size() < pageSize) {
                    break;
                }
                offset += pageSize;
            }

            if (result.isEmpty()) {
                throw new IllegalStateException("Marketstack 未返回有效股票数据");
            }

            if (result.size() > maxRows) {
                result = new ArrayList<>(result.subList(0, maxRows));
            }
            result.sort(Comparator.comparing(StockSnapshot::getDate).reversed()
                    .thenComparing(StockSnapshot::getSymbol));
            log.info("[行情服务] Marketstack 拉取完成，共 {} 条行情记录", result.size());
            return result;
        } catch (RestClientResponseException e) {
            if (!properties.isFallbackEnabled()) {
                throw e;
            }
            log.warn("[行情服务] Marketstack API 不可用，切换到本地兜底数据，HTTP状态={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return generateFallbackStocks(dateFrom, dateTo, maxRows);
        } catch (Exception e) {
            if (!properties.isFallbackEnabled()) {
                throw e;
            }
            log.warn("[行情服务] Marketstack 拉取异常，切换到本地兜底数据，错误={}", e.getMessage());
            return generateFallbackStocks(dateFrom, dateTo, maxRows);
        }
    }

    private List<StockSnapshot> generateFallbackStocks(LocalDate dateFrom, LocalDate dateTo, int maxRows) {
        List<StockSnapshot> result = new ArrayList<>();
        List<String> symbols = properties.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            return result;
        }

        long totalDays = Math.max(1L, dateTo.toEpochDay() - dateFrom.toEpochDay() + 1L);
        log.info("[行情服务] 生成本地兜底行情数据 symbolsCount={}, totalDays={}, maxRows={}",
                symbols.size(), totalDays, maxRows);

        for (int dayOffset = 0; dayOffset < totalDays && result.size() < maxRows; dayOffset++) {
            LocalDate tradeDate = dateTo.minusDays(dayOffset);
            for (int symbolIndex = 0; symbolIndex < symbols.size() && result.size() < maxRows; symbolIndex++) {
                result.add(buildFallbackSnapshot(symbols.get(symbolIndex), tradeDate, symbolIndex, dayOffset));
            }
        }

        result.sort(Comparator.comparing(StockSnapshot::getDate).reversed()
                .thenComparing(StockSnapshot::getSymbol));
        log.info("[行情服务] 本地兜底行情数据生成完成 count={}", result.size());
        return result;
    }

    private StockSnapshot buildFallbackSnapshot(String symbol, LocalDate tradeDate, int symbolIndex, int dayOffset) {
        BigDecimal anchor = BigDecimal.valueOf(35 + (symbolIndex % 12) * 18L + (symbol.length() % 5) * 7L);
        BigDecimal trend = BigDecimal.valueOf(dayOffset % 17L).multiply(BigDecimal.valueOf(0.43));
        BigDecimal wave = BigDecimal.valueOf((symbolIndex * 13L + dayOffset * 7L) % 9L).multiply(BigDecimal.valueOf(0.21));
        BigDecimal open = anchor.add(trend).add(wave).setScale(4, RoundingMode.HALF_UP);
        BigDecimal high = open.add(BigDecimal.valueOf(1.15 + (symbolIndex % 4) * 0.37)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal low = open.subtract(BigDecimal.valueOf(0.85 + (dayOffset % 3) * 0.19)).max(BigDecimal.valueOf(1)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal close = low.add(high.subtract(low).multiply(BigDecimal.valueOf(((symbolIndex + dayOffset) % 7 + 2) / 10.0)))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal volume = BigDecimal.valueOf(800_000L + symbolIndex * 37_000L + dayOffset * 9_000L);

        StockSnapshot snapshot = new StockSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setExchange(resolveExchange(symbolIndex));
        snapshot.setDate(tradeDate + "T16:00:00+0000");
        snapshot.setOpen(open);
        snapshot.setHigh(high);
        snapshot.setLow(low);
        snapshot.setClose(close);
        snapshot.setVolume(volume);
        return snapshot;
    }

    private String resolveExchange(int symbolIndex) {
        return switch (symbolIndex % 4) {
            case 0 -> "XNAS";
            case 1 -> "XNYS";
            case 2 -> "ARCX";
            default -> "BATS";
        };
    }

    @Data
    public static class StockSnapshot {
        private String symbol;
        private String exchange;
        private String date;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
    }

    @Data
    private static class MarketstackResponse {
        private Pagination pagination;
        private List<StockSnapshot> data;
    }

    @Data
    private static class Pagination {
        private Integer limit;
        private Integer offset;
        private Integer count;
        private Integer total;
    }
}
