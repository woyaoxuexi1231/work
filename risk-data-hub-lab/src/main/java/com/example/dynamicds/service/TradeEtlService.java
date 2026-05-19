package com.example.dynamicds.service;

import com.example.dynamicds.datasource.RoutingJdbcExecutor;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TradeEtlService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RoutingJdbcExecutor jdbcExecutor;
    private final DictionaryService dictionaryService;
    private final LeafSegmentService leafSegmentService;
    private final MessageOutboxService messageOutboxService;
    private final ExecutorService etlExecutor =
            Executors.newFixedThreadPool(3, new CustomizableThreadFactory("etl-biz-"));

    public TradeEtlService(RoutingJdbcExecutor jdbcExecutor,
                           DictionaryService dictionaryService,
                           LeafSegmentService leafSegmentService,
                           MessageOutboxService messageOutboxService) {
        this.jdbcExecutor = jdbcExecutor;
        this.dictionaryService = dictionaryService;
        this.leafSegmentService = leafSegmentService;
        this.messageOutboxService = messageOutboxService;
    }

    public Map<String, Object> runBootstrapClean() {
        List<String> systems = List.of(
                PlatformBootstrapService.DS_EQUITY,
                PlatformBootstrapService.DS_FUTURES,
                PlatformBootstrapService.DS_BOND
        );
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (String system : systems) {
            futures.add(CompletableFuture.supplyAsync(() -> cleanOneSystem(system, "INIT"), etlExecutor));
        }

        List<Map<String, Object>> details = futures.stream().map(CompletableFuture::join).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "INIT");
        result.put("details", details);
        result.put("cleanedCount", details.stream().mapToInt(item -> ((Number) item.get("cleanedCount")).intValue()).sum());
        return result;
    }

    public Map<String, Object> runRealtimeClean(String systemKey) {
        createRealtimeTrade(systemKey);
        return CompletableFuture.supplyAsync(() -> cleanOneSystem(systemKey, "REALTIME"), etlExecutor).join();
    }

    public List<Map<String, Object>> cleanedTrades() {
        return jdbcExecutor.query(PlatformBootstrapService.DS_WAREHOUSE, jdbc ->
                jdbc.queryForList("select global_id, source_system, vendor_trade_no, biz_type, direction, amount, status_name, counterparty_name, clean_mode, clean_batch, trade_time, created_at from clean_trade order by global_id desc limit 30"));
    }

    private Map<String, Object> cleanOneSystem(String systemKey, String mode) {
        List<Map<String, Object>> rows = jdbcExecutor.query(systemKey, jdbc ->
                jdbc.queryForList("select id, vendor_trade_no, biz_type, direction_code, amount, status_code, counterparty_code, trade_time from raw_trade where cleaned = 0 order by id"));
        int cleanedCount = 0;
        String batchNo = mode + "-" + System.currentTimeMillis();
        for (Map<String, Object> row : rows) {
            long globalId = leafSegmentService.nextId("clean_trade");
            String statusName = dictionaryService.translate("trade_status", String.valueOf(row.get("status_code")));
            String counterpartyName = dictionaryService.translate("counterparty", String.valueOf(row.get("counterparty_code")));
            String direction = "B".equals(row.get("direction_code")) ? "BUY" : "SELL";
            jdbcExecutor.run(PlatformBootstrapService.DS_WAREHOUSE, jdbc -> jdbc.update(
                    "insert into clean_trade(global_id, source_system, vendor_trade_no, biz_type, direction, amount, status_name, counterparty_name, clean_mode, clean_batch, trade_time, created_at) " +
                            "values (?,?,?,?,?,?,?,?,?,?,?,?)",
                    globalId,
                    systemKey,
                    row.get("vendor_trade_no"),
                    row.get("biz_type"),
                    direction,
                    row.get("amount"),
                    statusName,
                    counterpartyName,
                    mode,
                    batchNo,
                    row.get("trade_time"),
                    LocalDateTime.now().format(FORMATTER)));

            jdbcExecutor.run(systemKey, jdbc -> jdbc.update("update raw_trade set cleaned = 1 where id = ?", row.get("id")));

            String payload = "{"
                    + "\"globalId\":" + globalId + ","
                    + "\"sourceSystem\":\"" + systemKey + "\","
                    + "\"vendorTradeNo\":\"" + row.get("vendor_trade_no") + "\","
                    + "\"statusName\":\"" + statusName + "\""
                    + "}";
            messageOutboxService.publish("risk.trade.cleaned", String.valueOf(globalId), payload);
            cleanedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemKey", systemKey);
        result.put("threadName", Thread.currentThread().getName());
        result.put("mode", mode);
        result.put("cleanedCount", cleanedCount);
        result.put("batchNo", batchNo);
        return result;
    }

    private void createRealtimeTrade(String systemKey) {
        String prefix = switch (systemKey) {
            case PlatformBootstrapService.DS_EQUITY -> "EQT-RT";
            case PlatformBootstrapService.DS_FUTURES -> "FUT-RT";
            case PlatformBootstrapService.DS_BOND -> "BND-RT";
            default -> throw new IllegalArgumentException("未知交易系统: " + systemKey);
        };
        String bizType = switch (systemKey) {
            case PlatformBootstrapService.DS_EQUITY -> "股票";
            case PlatformBootstrapService.DS_FUTURES -> "期货";
            case PlatformBootstrapService.DS_BOND -> "债券";
            default -> throw new IllegalArgumentException("未知交易系统: " + systemKey);
        };

        jdbcExecutor.run(systemKey, jdbc -> jdbc.update(
                "insert into raw_trade(vendor_trade_no, biz_type, direction_code, amount, status_code, counterparty_code, trade_time, cleaned) values (?,?,?,?,?,?,?,0)",
                prefix + "-" + System.currentTimeMillis(),
                bizType,
                System.currentTimeMillis() % 2 == 0 ? "B" : "S",
                BigDecimal.valueOf(3000 + (System.currentTimeMillis() % 1000)),
                "1",
                "C001",
                LocalDateTime.now().format(FORMATTER)));
    }
}
