package com.example.dynamicds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "marketstack")
public class MarketstackProperties {
    private boolean enabled = true;
    private String baseUrl = "https://api.marketstack.com/v2";
    private String accessKey;
    private List<String> symbols = List.of(
            "AAPL", "MSFT", "NVDA", "AMZN", "META",
            "TSLA", "GOOGL", "AMD", "NFLX", "INTC",
            "ORCL", "IBM", "JPM", "BAC", "WMT"
    );
}
