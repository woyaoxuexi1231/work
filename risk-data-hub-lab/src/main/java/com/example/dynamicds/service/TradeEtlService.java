package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingJdbcExecutor;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.entity.CleanTrade;
import com.example.dynamicds.mapper.CleanTradeMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeEtlService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DynamicDataSourceManager dataSourceManager;
    private final RoutingJdbcExecutor routingJdbcExecutor;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final DictionaryService dictionaryService;
    private final LeafSegmentService leafSegmentService;
    private final MessageOutboxService messageOutboxService;
    private final CleanTradeMapper cleanTradeMapper;

    public Map<String, Object> syncByDataSource(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = requireSyncableConfig(dataSourceKey);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        String batchNo = "SYNC-" + System.currentTimeMillis();
        log.info("[ETL] 开始同步 dataSourceKey={}, datasourceType={}, pageSize={}",
                dataSourceKey, config.getDatasourceType(), safePageSize);

        long lastId = 0L;
        int pageNo = 0;
        int pulledCount = 0;
        int savedCount = 0;
        List<Map<String, Object>> pageDetails = new ArrayList<>();

        while (true) {
            List<SourceRow> sourceRows = fetchPage(dataSourceKey, config.getDatasourceType(), lastId, safePageSize);
            if (sourceRows.isEmpty()) {
                break;
            }

            pageNo++;
            int currentPageSaved = 0;
            for (SourceRow sourceRow : sourceRows) {
                CleanTrade cleanTrade = transformRow(dataSourceKey, config.getDatasourceType(), sourceRow, batchNo);
                routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> cleanTradeMapper.insert(cleanTrade));
                markSourceRowSynced(dataSourceKey, config.getDatasourceType(), sourceRow.getId());
                publishSyncEvent(cleanTrade);
                pulledCount++;
                savedCount++;
                currentPageSaved++;
                lastId = sourceRow.getId();
                log.info("[ETL] 同步成功 dataSourceKey={}, type={}, sourceRowId={}, globalId={}",
                        dataSourceKey, config.getDatasourceType(), sourceRow.getId(), cleanTrade.getGlobalId());
            }

            Map<String, Object> pageInfo = new LinkedHashMap<>();
            pageInfo.put("pageNo", pageNo);
            pageInfo.put("rowCount", sourceRows.size());
            pageInfo.put("savedCount", currentPageSaved);
            pageInfo.put("lastRowId", lastId);
            pageDetails.add(pageInfo);

            if (sourceRows.size() < safePageSize) {
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataSourceKey", dataSourceKey);
        result.put("dataSourceName", config.getName());
        result.put("datasourceType", config.getDatasourceType());
        result.put("pageSize", safePageSize);
        result.put("pageCount", pageNo);
        result.put("pulledCount", pulledCount);
        result.put("savedCount", savedCount);
        result.put("batchNo", batchNo);
        result.put("pages", pageDetails);
        return result;
    }

    public List<CleanTrade> cleanedTrades() {
        return routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB,
                () -> cleanTradeMapper.selectList(new LambdaQueryWrapper<CleanTrade>()
                        .orderByDesc(CleanTrade::getGlobalId)
                        .last("limit 30")));
    }

    private DataSourceConfigDTO requireSyncableConfig(String dataSourceKey) {
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (PlatformBootstrapService.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }
        return config;
    }

    private List<SourceRow> fetchPage(String dataSourceKey, String datasourceType, long lastId, int pageSize) {
        return switch (datasourceType) {
            case PlatformBootstrapService.TYPE_TRADE_OMS -> fetchOmsTradePage(dataSourceKey, lastId, pageSize);
            case PlatformBootstrapService.TYPE_TRADE_BROKER -> fetchBrokerTradePage(dataSourceKey, lastId, pageSize);
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + datasourceType);
        };
    }

    private List<SourceRow> fetchOmsTradePage(String dataSourceKey, long lastId, int pageSize) {
        String sql = """
                select id, order_no, investor_name, side_code, order_amount, trade_status, trade_time
                from oms_trade_order
                where sync_flag = 0 and id > ?
                order by id asc
                limit ?
                """;
        return routingJdbcExecutor.query(dataSourceKey, jdbc -> jdbc.query(sql, (rs, rowNum) -> {
            SourceRow row = new SourceRow();
            row.setId(rs.getLong("id"));
            row.setBizNo(rs.getString("order_no"));
            row.setDisplayName(rs.getString("investor_name"));
            row.setRawDirection(rs.getString("side_code"));
            row.setAmount(rs.getBigDecimal("order_amount"));
            row.setRawStatus(rs.getString("trade_status"));
            row.setTradeTime(rs.getString("trade_time"));
            return row;
        }, lastId, pageSize));
    }

    private List<SourceRow> fetchBrokerTradePage(String dataSourceKey, long lastId, int pageSize) {
        String sql = """
                select id, deal_code, client_full_name, bs_flag, turnover_amount, status_mark, deal_at
                from broker_trade_deal
                where sync_flag = 0 and id > ?
                order by id asc
                limit ?
                """;
        return routingJdbcExecutor.query(dataSourceKey, jdbc -> jdbc.query(sql, (rs, rowNum) -> {
            SourceRow row = new SourceRow();
            row.setId(rs.getLong("id"));
            row.setBizNo(rs.getString("deal_code"));
            row.setDisplayName(rs.getString("client_full_name"));
            row.setRawDirection(rs.getString("bs_flag"));
            row.setAmount(rs.getBigDecimal("turnover_amount"));
            row.setRawStatus(rs.getString("status_mark"));
            row.setTradeTime(rs.getString("deal_at"));
            return row;
        }, lastId, pageSize));
    }

    private CleanTrade transformRow(String dataSourceKey, String datasourceType, SourceRow sourceRow, String batchNo) {
        return switch (datasourceType) {
            case PlatformBootstrapService.TYPE_TRADE_OMS -> transformOmsTradeRow(dataSourceKey, sourceRow, batchNo);
            case PlatformBootstrapService.TYPE_TRADE_BROKER -> transformBrokerTradeRow(dataSourceKey, sourceRow, batchNo);
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + datasourceType);
        };
    }

    private CleanTrade transformOmsTradeRow(String dataSourceKey, SourceRow sourceRow, String batchNo) {
        CleanTrade cleanTrade = baseTrade(dataSourceKey, PlatformBootstrapService.TYPE_TRADE_OMS, sourceRow, batchNo);
        cleanTrade.setBizType("股票交易");
        cleanTrade.setDirection("B".equalsIgnoreCase(sourceRow.getRawDirection()) ? "BUY" : "SELL");
        cleanTrade.setStatusName(dictionaryService.translate("trade_status_oms", sourceRow.getRawStatus()));
        cleanTrade.setCounterpartyName(sourceRow.getDisplayName());
        return cleanTrade;
    }

    private CleanTrade transformBrokerTradeRow(String dataSourceKey, SourceRow sourceRow, String batchNo) {
        CleanTrade cleanTrade = baseTrade(dataSourceKey, PlatformBootstrapService.TYPE_TRADE_BROKER, sourceRow, batchNo);
        cleanTrade.setBizType("股票交易");
        cleanTrade.setDirection("1".equals(sourceRow.getRawDirection()) ? "BUY" : "SELL");
        cleanTrade.setStatusName(dictionaryService.translate("trade_status_broker", sourceRow.getRawStatus()));
        cleanTrade.setCounterpartyName(sourceRow.getDisplayName());
        return cleanTrade;
    }

    private CleanTrade baseTrade(String dataSourceKey, String datasourceType, SourceRow sourceRow, String batchNo) {
        CleanTrade cleanTrade = new CleanTrade();
        cleanTrade.setGlobalId(leafSegmentService.nextId("clean_trade"));
        cleanTrade.setSourceSystem(dataSourceKey);
        cleanTrade.setSourceType(datasourceType);
        cleanTrade.setSourceRowId(sourceRow.getId());
        cleanTrade.setVendorTradeNo(sourceRow.getBizNo());
        cleanTrade.setAmount(sourceRow.getAmount());
        cleanTrade.setTradeTime(sourceRow.getTradeTime());
        cleanTrade.setCleanMode("MANUAL_SYNC");
        cleanTrade.setCleanBatch(batchNo);
        cleanTrade.setCreatedAt(LocalDateTime.now().format(FORMATTER));
        return cleanTrade;
    }

    private void markSourceRowSynced(String dataSourceKey, String datasourceType, long rowId) {
        switch (datasourceType) {
            case PlatformBootstrapService.TYPE_TRADE_OMS ->
                    routingJdbcExecutor.run(dataSourceKey, jdbc ->
                            jdbc.update("update oms_trade_order set sync_flag = 1 where id = ?", rowId));
            case PlatformBootstrapService.TYPE_TRADE_BROKER ->
                    routingJdbcExecutor.run(dataSourceKey, jdbc ->
                            jdbc.update("update broker_trade_deal set sync_flag = 1 where id = ?", rowId));
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + datasourceType);
        }
    }

    private void publishSyncEvent(CleanTrade cleanTrade) {
        String payload = "{"
                + "\"globalId\":" + cleanTrade.getGlobalId() + ","
                + "\"sourceSystem\":\"" + cleanTrade.getSourceSystem() + "\","
                + "\"sourceType\":\"" + cleanTrade.getSourceType() + "\","
                + "\"bizNo\":\"" + cleanTrade.getVendorTradeNo() + "\""
                + "}";
        messageOutboxService.publish("risk.sync.completed", String.valueOf(cleanTrade.getGlobalId()), payload);
    }

    @Data
    private static class SourceRow {
        private Long id;
        private String bizNo;
        private String displayName;
        private BigDecimal amount;
        private String rawDirection;
        private String rawStatus;
        private String tradeTime;
    }
}
