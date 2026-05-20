package com.example.dynamicds.service;

import com.example.dynamicds.config.MarketstackProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketstackService {

    private final RestClient.Builder restClientBuilder;
    private final MarketstackProperties properties;

    public List<StockSnapshot> fetchLatestStocks() {
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
        log.info("[Marketstack] 开始拉取股票数据 symbols={}", symbols);

        MarketstackResponse response = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/eod/latest")
                        .queryParam("access_key", properties.getAccessKey())
                        .queryParam("symbols", symbols)
                        .build())
                .retrieve()
                .body(MarketstackResponse.class);

        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw new IllegalStateException("Marketstack 未返回有效股票数据");
        }
        log.info("[Marketstack] 拉取完成 count={}", response.getData().size());
        return response.getData();
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
        private List<StockSnapshot> data;
    }
}
