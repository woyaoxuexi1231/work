package com.example.dynamicds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "marketstack")
public class MarketstackProperties {
    private boolean enabled = true;
    private boolean fallbackEnabled = true;
    private String baseUrl = "https://api.marketstack.com/v2";
    private String accessKey;
    private int lookbackDays = 120;
    private int pageSize = 200;
    private int maxRows = 2000;
    private List<String> symbols = List.of(
            "AAPL", "MSFT", "NVDA", "AMZN", "META",
            "TSLA", "GOOGL", "AMD", "NFLX", "INTC",
            "ORCL", "IBM", "JPM", "BAC", "WMT",
            "TSM", "QCOM", "AVGO", "ADBE", "CRM",
            "CSCO", "UBER", "PYPL", "SHOP", "SQ",
            "COIN", "MU", "ARM", "SONY", "BABA",
            "PDD", "BIDU", "NIO", "XPEV", "LI",
            "DIS", "KO", "MCD", "PEP", "COST", "HD"
    );
}
