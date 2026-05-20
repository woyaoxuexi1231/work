package com.example.dynamicds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Marketstack 行情 API 配置。
 * <p>
 * <b>设计要点：</b>
 * <ul>
 *   <li><b>enabled + fallbackEnabled 分离控制</b> — 可以开启行情但关闭兜底（方便调试），
 *       也可以关闭行情直接使用兜底数据（无 API 密钥时）。</li>
 *   <li><b>默认值全部在字段上直接赋值</b> — 而不是在 application.yml 中定义。
 *       这样即使 application.yml 中没有 marketstack 配置段，也能直接用默认值启动。
 *       新增字段时也无需修改配置文件，减少配置遗漏的风险。</li>
 *   <li><b>symbols 使用 List.of()</b> — 返回不可变列表，防止运行时被意外修改。</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "marketstack")
public class MarketstackProperties {
    /** 是否启用 Marketstack 远程拉取 */
    private boolean enabled = true;
    /** 远程拉取失败时是否启用本地兜底数据 */
    private boolean fallbackEnabled = true;
    /** Marketstack API 基础地址 */
    private String baseUrl = "https://api.marketstack.com/v2";
    /** API 访问密钥 */
    private String accessKey;
    /** 回溯天数（从今天往前推） */
    private int lookbackDays = 120;
    /** 每页条数 */
    private int pageSize = 200;
    /** 最大拉取行数（防止过度消耗 API 配额） */
    private int maxRows = 2000;
    /** 股票代码列表 */
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
