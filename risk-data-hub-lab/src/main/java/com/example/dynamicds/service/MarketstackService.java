package com.example.dynamicds.service;

import com.example.dynamicds.config.MarketstackProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

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
        if (!StringUtils.hasText(properties.getAccessKey())) {
            throw new IllegalStateException("未配置 marketstack.access-key 或环境变量 MARKETSTACK_ACCESS_KEY");
        }
        if (properties.getSymbols() == null || properties.getSymbols().isEmpty()) {
            throw new IllegalStateException("marketstack.symbols 不能为空");
        }

        String symbols = String.join(",", properties.getSymbols());
        int pageSize = Math.max(1, Math.min(properties.getPageSize(), 1000));
        int maxRows = Math.max(pageSize, properties.getMaxRows());
        LocalDate dateTo = LocalDate.now();
        LocalDate dateFrom = dateTo.minusDays(Math.max(1, properties.getLookbackDays()));
        log.info("[Marketstack] 开始拉取历史股票数据 symbols={}, dateFrom={}, dateTo={}, pageSize={}, maxRows={}",
                symbols, dateFrom, dateTo, pageSize, maxRows);

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
        log.info("[Marketstack] 拉取完成 count={}", result.size());
        return result;
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
