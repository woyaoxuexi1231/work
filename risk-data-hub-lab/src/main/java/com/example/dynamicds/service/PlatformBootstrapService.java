package com.example.dynamicds.service;

import com.example.dynamicds.config.HubDataSourceProperties;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.entity.BrokerFundAccount;
import com.example.dynamicds.entity.BrokerPositionBalance;
import com.example.dynamicds.entity.BrokerStockQuote;
import com.example.dynamicds.entity.BrokerTradeDeal;
import com.example.dynamicds.entity.DictItem;
import com.example.dynamicds.entity.LeafAlloc;
import com.example.dynamicds.entity.OmsCashAsset;
import com.example.dynamicds.entity.OmsPositionHolding;
import com.example.dynamicds.entity.OmsStockSnapshot;
import com.example.dynamicds.entity.OmsTradeOrder;
import com.example.dynamicds.mapper.BrokerFundAccountMapper;
import com.example.dynamicds.mapper.BrokerPositionBalanceMapper;
import com.example.dynamicds.mapper.BrokerStockQuoteMapper;
import com.example.dynamicds.mapper.BrokerTradeDealMapper;
import com.example.dynamicds.mapper.DictItemMapper;
import com.example.dynamicds.mapper.DynamicSqlMapper;
import com.example.dynamicds.mapper.LeafAllocMapper;
import com.example.dynamicds.mapper.OmsCashAssetMapper;
import com.example.dynamicds.mapper.OmsPositionHoldingMapper;
import com.example.dynamicds.mapper.OmsStockSnapshotMapper;
import com.example.dynamicds.mapper.OmsTradeOrderMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 平台初始化服务 — 系统启动引导 + 演示数据灌数。
 *
 * 职责：
 * 1. @PostConstruct init()：自动创建三个数据库的 schema、注册数据源、创建表结构
 * 2. initDemoData()：前端手动触发，在本地生成伪随机股票数据，
 *    分别写入 trade_oms 和 trade_broker 两个异构上游系统
 *
 * 两个上游系统的表结构不同（oms_* 和 broker_* 前缀不同），
 * 但概念一一对应：股票快照/行情、交易/成交、持仓、资金/资产。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlatformBootstrapService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> OMS_STATUSES = List.of("NEW", "DONE", "CANCEL");
    private static final List<String> BROKER_STATUSES = List.of("A", "S", "X");
    private static final List<String> OMS_ACCOUNTS = List.of("量化一号", "量化二号", "多因子策略", "中性策略", "高频策略");
    private static final List<String> BROKER_CLIENTS = List.of("华泰资管账户", "中信机构账户", "国君量化账户", "招商自营账户", "东方财富量化户");
    private static final int OMS_ORDER_REPEAT = 3;
    private static final int BROKER_DEAL_REPEAT = 4;
    private static final int BOOTSTRAP_PROGRESS_STEP = 200;

    public static final String DS_HUB = "risk_hub";
    public static final String DS_TRADE_OMS = "trade_oms";
    public static final String DS_TRADE_BROKER = "trade_broker";

    public static final String TYPE_HUB = "HUB";
    public static final String TYPE_TRADE_OMS = "TRADE_OMS";
    public static final String TYPE_TRADE_BROKER = "TRADE_BROKER";

    private final DynamicDataSourceManager manager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final LeafSegmentService leafSegmentService;
    private final HubDataSourceProperties properties;
    private final DynamicSqlMapper dynamicSqlMapper;
    private final DictItemMapper dictItemMapper;
    private final LeafAllocMapper leafAllocMapper;
    private final OmsStockSnapshotMapper omsStockSnapshotMapper;
    private final OmsTradeOrderMapper omsTradeOrderMapper;
    private final OmsPositionHoldingMapper omsPositionHoldingMapper;
    private final OmsCashAssetMapper omsCashAssetMapper;
    private final BrokerStockQuoteMapper brokerStockQuoteMapper;
    private final BrokerTradeDealMapper brokerTradeDealMapper;
    private final BrokerPositionBalanceMapper brokerPositionBalanceMapper;
    private final BrokerFundAccountMapper brokerFundAccountMapper;

    /**
     * 系统启动后自动执行：创建数据库 → 注册数据源 → 创建所有表结构。
     * 演示数据不在启动时灌入，由前端手动触发 initDemoData()。
     */
    @PostConstruct
    public void init() {
        log.info("[平台初始化] 开始自动创建 schema、注册数据源并校验表结构");
        ensureSchemas();
        ensureDataSource(DS_HUB);
        ensureDataSource(DS_TRADE_OMS);
        ensureDataSource(DS_TRADE_BROKER);
        ensureLatestSchema();
        leafSegmentService.clearLocalCache();
        log.info("[平台初始化] schema 和表结构准备完成，演示数据请从前端手动初始化");
    }

    /**
     * 初始化演示数据：清空所有表 → 灌入字典和发号器 → 生成本地股票数据 →
     * 按上游系统各自的表结构分别写入 trade_oms 和 trade_broker。
     * <p>
     * <b>设计说明：</b>
     * <ul>
     *   <li><b>synchronized</b> — 防止前端连续点击"初始化"按钮导致并发灌数。
     *       如果两次初始化同时执行，第一次 truncate 后第二次 insert 的数据可能被第一次清掉，
     *       最终数据不一致。</li>
     *   <li><b>与 InitDataTaskService 的 AtomicBoolean 双重保护</b> —
     *       InitDataTaskService.startTask() 的 CAS 确保"同一时刻只有一个初始化任务在执行"，
     *       initDemoData() 的 synchronized 确保"即使绕过任务服务直接调用也不会并发"。</li>
     * </ul>
     */
    public synchronized Map<String, Object> initDemoData() {
        log.info("[平台初始化] 开始按当前表结构初始化演示数据");
        clearAllTableData();
        initHubBaseData();
        List<StockSnapshot> snapshots = generateFallbackStocks();
        seedTradeSystemsFromMarketData(snapshots);
        leafSegmentService.clearLocalCache();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotCount", snapshots.size());
        result.put("businessTableStats", currentBusinessTableStats());
        result.put("hubTableStats", currentHubTableStats());
        log.info("[平台初始化] 演示数据初始化完成，股票基础样本条数={}, 业务表统计={}", snapshots.size(), currentBusinessTableStats());
        return result;
    }

    /**
     * 带进度回调的初始化方法 - 用于实时更新任务进度
     * 进度分配：
     * - 0-5%: 清空表 + 基础数据
     * - 5-15%: 生成股票数据
     * - 15-60%: 写入交易系统A (oms_*)
     * - 60-95%: 写入交易系统B (broker_*)
     * - 95-100%: 清理缓存 + 汇总
     */
    public synchronized Map<String, Object> initDemoDataWithProgress(IntConsumer progressCallback) {
        log.info("[平台初始化] 开始按当前表结构初始化演示数据（带进度）");
        
        // 阶段1: 清空表 (0-5%)
        progressCallback.accept(0);
        clearAllTableData();
        progressCallback.accept(3);
        initHubBaseData();
        progressCallback.accept(5);
        
        // 阶段2: 生成股票数据 (5-15%)
        List<StockSnapshot> snapshots = generateFallbackStocks();
        progressCallback.accept(15);
        
        // 阶段3: 写入交易系统A (15-60%)
        seedTradeSystemsFromMarketDataWithProgress(snapshots, progressCallback, 15, 60);
        
        // 阶段4: 写入交易系统B (60-95%)
        seedBrokerSystemsFromMarketDataWithProgress(snapshots, progressCallback, 60, 95);
        
        // 阶段5: 清理缓存 + 汇总 (95-100%)
        progressCallback.accept(95);
        leafSegmentService.clearLocalCache();
        progressCallback.accept(100);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotCount", snapshots.size());
        result.put("businessTableStats", currentBusinessTableStats());
        result.put("hubTableStats", currentHubTableStats());
        log.info("[平台初始化] 演示数据初始化完成，股票基础样本条数={}, 业务表统计={}", snapshots.size(), currentBusinessTableStats());
        return result;
    }

    public Map<String, Object> currentTopology() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hub", DS_HUB);
        map.put("upstreams", List.of(
                Map.of(
                        "key", DS_TRADE_OMS,
                        "type", TYPE_TRADE_OMS,
                        "syncTables", List.of("oms_stock_snapshot", "oms_trade_order", "oms_position_holding", "oms_cash_asset"),
                        "tables", List.of("oms_stock_snapshot", "oms_trade_order", "oms_position_holding", "oms_cash_asset")),
                Map.of(
                        "key", DS_TRADE_BROKER,
                        "type", TYPE_TRADE_BROKER,
                        "syncTables", List.of("broker_stock_quote", "broker_trade_deal", "broker_position_balance", "broker_fund_account"),
                        "tables", List.of("broker_stock_quote", "broker_trade_deal", "broker_position_balance", "broker_fund_account"))
        ));
        return map;
    }

    public Map<String, Object> currentBusinessTableStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(DS_TRADE_OMS, countTables(DS_TRADE_OMS, List.of(
                "oms_stock_snapshot", "oms_trade_order", "oms_position_holding", "oms_cash_asset")));
        result.put(DS_TRADE_BROKER, countTables(DS_TRADE_BROKER, List.of(
                "broker_stock_quote", "broker_trade_deal", "broker_position_balance", "broker_fund_account")));
        return result;
    }

    public Map<String, Integer> currentHubTableStats() {
        return countTables(DS_HUB, List.of(
                "clean_stock", "clean_trade", "clean_position", "clean_asset", "event_message"));
    }

    /**
     * 确保三个数据库（risk_hub, trade_oms, trade_broker）已存在。
     * 使用不带数据库名的管理连接执行 CREATE DATABASE IF NOT EXISTS。
     */
    private void ensureSchemas() {
        HubDataSourceProperties.Item adminItem = properties.findRequired(properties.getDefaultKey());
        String bootstrapUrl = toBootstrapUrl(adminItem.getUrl());
        try (Connection connection = DriverManager.getConnection(
                bootstrapUrl,
                adminItem.getUsername(),
                adminItem.getPassword());
             Statement statement = connection.createStatement()) {
            for (HubDataSourceProperties.Item item : properties.getItems()) {
                String schemaName = extractSchemaName(item.getUrl());
                statement.execute("create database if not exists `" + schemaName + "` default character set utf8mb4");
                log.info("[平台初始化] schema 已确认存在 schemaName={}", schemaName);
            }
        } catch (Exception e) {
            throw new IllegalStateException("自动创建 schema 失败: " + e.getMessage(), e);
        }
    }

    private void ensureLatestSchema() {
        createHubTables();
        createOmsTradeTables();
        createBrokerTradeTables();
    }

    private void ensureDataSource(String key) {
        if (manager.exists(key)) {
            return;
        }
        HubDataSourceProperties.Item item = properties.findRequired(key);
        DataSourceConfigDTO config = new DataSourceConfigDTO();
        config.setKey(key);
        config.setName(item.getName());
        config.setDatasourceType(item.getDatasourceType());
        config.setUsername(item.getUsername());
        config.setPassword(item.getPassword());
        config.setDriverClassName(item.getDriverClassName());
        config.setPoolName("HikariPool-" + item.getName());
        config.setMaxPoolSize(item.getMaxPoolSize());
        config.setMinIdle(item.getMinIdle());
        config.setConnectionTimeout(item.getConnectionTimeout());
        config.setIdleTimeout(item.getIdleTimeout());
        config.setMaxLifetime(item.getMaxLifetime());
        config.setUrl(item.getUrl());
        log.info("[平台初始化] 注册数据源 key={}, type={}, url={}", key, item.getDatasourceType(), item.getUrl());
        manager.register(config);
    }

    private void initHubBaseData() {
        routingMybatisExecutor.run(DS_HUB, () -> {
            dictItemMapper.insert(buildDictItem("trade_status_oms", "NEW", "待确认", "交易系统A待确认状态"));
            dictItemMapper.insert(buildDictItem("trade_status_oms", "DONE", "已成交", "交易系统A成交完成"));
            dictItemMapper.insert(buildDictItem("trade_status_oms", "CANCEL", "已撤单", "交易系统A撤单状态"));
            dictItemMapper.insert(buildDictItem("trade_status_broker", "A", "待确认", "交易系统B待确认状态"));
            dictItemMapper.insert(buildDictItem("trade_status_broker", "S", "已成交", "交易系统B成交完成"));
            dictItemMapper.insert(buildDictItem("trade_status_broker", "X", "已撤单", "交易系统B撤单状态"));

            leafAllocMapper.insert(buildLeafAlloc("clean_stock", 50000L, 20, "中台标准股票主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_trade", 100000L, 20, "中台标准交易主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_position", 200000L, 20, "中台标准持仓主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_asset", 300000L, 20, "中台标准资金主键"));
            leafAllocMapper.insert(buildLeafAlloc("event_message", 500000L, 20, "同步事件主键"));
            leafAllocMapper.insert(buildLeafAlloc("tx_audit", 900000L, 10, "事务审计主键"));
        });
    }

    private void clearAllTableData() {
        clearBrokerTradeTables();
        clearOmsTradeTables();
        clearHubTables();
    }

    private void clearHubTables() {
        routingMybatisExecutor.run(DS_HUB, () -> {
            executeSql("truncate table sync_business_record");
            executeSql("truncate table sync_task");
            executeSql("truncate table init_task");
            executeSql("truncate table tx_coordination_log");
            executeSql("truncate table event_message");
            executeSql("truncate table clean_asset");
            executeSql("truncate table clean_position");
            executeSql("truncate table clean_trade");
            executeSql("truncate table clean_stock");
            executeSql("truncate table leaf_alloc");
            executeSql("truncate table dict_item");
        });
    }

    private void clearOmsTradeTables() {
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            executeSql("truncate table oms_cash_asset");
            executeSql("truncate table oms_position_holding");
            executeSql("truncate table oms_trade_order");
            executeSql("truncate table oms_stock_snapshot");
        });
    }

    private void clearBrokerTradeTables() {
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            executeSql("truncate table broker_fund_account");
            executeSql("truncate table broker_position_balance");
            executeSql("truncate table broker_trade_deal");
            executeSql("truncate table broker_stock_quote");
        });
    }

    /**
     * 从本地生成的股票行情数据，分别写入
     * trade_oms（oms_* 前缀）和 trade_broker（broker_* 前缀）两个异构上游系统。
     * 每只股票生成：快照/行情 1 条、交易/成交 多条、持仓 1 条、资金/资产 伪随机 1 条。
     */
    private void seedTradeSystemsFromMarketData(List<StockSnapshot> snapshots) {
        log.info("[平台初始化] 开始写入交易系统A业务表，股票样本数={}", snapshots.size());
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            for (int i = 0; i < snapshots.size(); i++) {
                StockSnapshot snapshot = snapshots.get(i);
                insertOmsStockSnapshot(snapshot, i + 1);
                for (int j = 0; j < OMS_ORDER_REPEAT; j++) {
                    insertOmsTradeSeed(snapshot, i + 1, j + 1);
                }
                insertOmsPositionSeed(snapshot, i + 1);
                if (i % 3 == 0) {
                    insertOmsCashSeed(snapshot, i + 1);
                }
                logBootstrapProgress("交易系统A", i + 1, snapshots.size());
            }
        });
        log.info("[平台初始化] 交易系统A业务表写入完成");

        log.info("[平台初始化] 开始写入交易系统B业务表，股票样本数={}", snapshots.size());
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            for (int i = 0; i < snapshots.size(); i++) {
                StockSnapshot snapshot = snapshots.get(i);
                insertBrokerStockQuote(snapshot, i + 1);
                for (int j = 0; j < BROKER_DEAL_REPEAT; j++) {
                    insertBrokerTradeSeed(snapshot, i + 1, j + 1);
                }
                insertBrokerPositionSeed(snapshot, i + 1);
                if (i % 3 == 0) {
                    insertBrokerFundSeed(snapshot, i + 1);
                }
                logBootstrapProgress("交易系统B", i + 1, snapshots.size());
            }
        });
        log.info("[平台初始化] 交易系统B业务表写入完成");
    }

    /**
     * 带进度的交易系统A写入方法
     */
    private void seedTradeSystemsFromMarketDataWithProgress(List<StockSnapshot> snapshots, 
                                                            IntConsumer progressCallback,
                                                            int progressStart, int progressEnd) {
        log.info("[平台初始化] 开始写入交易系统A业务表（带进度），股票样本数={}", snapshots.size());
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            int totalSteps = snapshots.size();
            for (int i = 0; i < snapshots.size(); i++) {
                StockSnapshot snapshot = snapshots.get(i);
                insertOmsStockSnapshot(snapshot, i + 1);
                for (int j = 0; j < OMS_ORDER_REPEAT; j++) {
                    insertOmsTradeSeed(snapshot, i + 1, j + 1);
                }
                insertOmsPositionSeed(snapshot, i + 1);
                if (i % 3 == 0) {
                    insertOmsCashSeed(snapshot, i + 1);
                }
                // 更新进度
                int progress = progressStart + (int) ((i + 1) / (double) totalSteps * (progressEnd - progressStart));
                progressCallback.accept(progress);
            }
        });
        log.info("[平台初始化] 交易系统A业务表写入完成");
    }

    /**
     * 带进度的交易系统B写入方法
     */
    private void seedBrokerSystemsFromMarketDataWithProgress(List<StockSnapshot> snapshots,
                                                              IntConsumer progressCallback,
                                                              int progressStart, int progressEnd) {
        log.info("[平台初始化] 开始写入交易系统B业务表（带进度），股票样本数={}", snapshots.size());
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            int totalSteps = snapshots.size();
            for (int i = 0; i < snapshots.size(); i++) {
                StockSnapshot snapshot = snapshots.get(i);
                insertBrokerStockQuote(snapshot, i + 1);
                for (int j = 0; j < BROKER_DEAL_REPEAT; j++) {
                    insertBrokerTradeSeed(snapshot, i + 1, j + 1);
                }
                insertBrokerPositionSeed(snapshot, i + 1);
                if (i % 3 == 0) {
                    insertBrokerFundSeed(snapshot, i + 1);
                }
                // 更新进度
                int progress = progressStart + (int) ((i + 1) / (double) totalSteps * (progressEnd - progressStart));
                progressCallback.accept(progress);
            }
        });
        log.info("[平台初始化] 交易系统B业务表写入完成");
    }

    private void logBootstrapProgress(String systemName, int current, int total) {
        if (current == total || current % BOOTSTRAP_PROGRESS_STEP == 0) {
            log.info("[平台初始化] {} 灌数进度 {}/{}", systemName, current, total);
        }
    }

    private void dropHubTables() {
        routingMybatisExecutor.run(DS_HUB, () -> {
            executeSql("drop table if exists sync_business_record");
            executeSql("drop table if exists sync_task");
            executeSql("drop table if exists init_task");
            executeSql("drop table if exists tx_coordination_log");
            executeSql("drop table if exists event_message");
            executeSql("drop table if exists clean_asset");
            executeSql("drop table if exists clean_position");
            executeSql("drop table if exists clean_trade");
            executeSql("drop table if exists clean_stock");
            executeSql("drop table if exists leaf_alloc");
            executeSql("drop table if exists dict_item");
        });
    }

    private void dropOmsTradeTables() {
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            executeSql("drop table if exists oms_cash_asset");
            executeSql("drop table if exists oms_position_holding");
            executeSql("drop table if exists oms_trade_order");
            executeSql("drop table if exists oms_stock_snapshot");
        });
    }

    private void dropBrokerTradeTables() {
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            executeSql("drop table if exists broker_fund_account");
            executeSql("drop table if exists broker_position_balance");
            executeSql("drop table if exists broker_trade_deal");
            executeSql("drop table if exists broker_stock_quote");
        });
    }

    private void createHubTables() {
        routingMybatisExecutor.run(DS_HUB, () -> {
            executeSql("create table if not exists dict_item (" +
                    "id bigint not null auto_increment primary key," +
                    "dict_type varchar(64) not null," +
                    "dict_code varchar(64) not null," +
                    "dict_name varchar(128) not null," +
                    "dict_desc varchar(256)," +
                    "unique key uk_dict_type_code(dict_type, dict_code))");
            executeSql("create table if not exists leaf_alloc (" +
                    "biz_tag varchar(64) primary key," +
                    "max_id bigint not null," +
                    "step int not null," +
                    "description varchar(256))");
            executeSql("create table if not exists clean_stock (" +
                    "global_id bigint primary key," +
                    "source_system varchar(64) not null," +
                    "source_type varchar(32) not null," +
                    "source_row_id bigint not null," +
                    "stock_code varchar(32) not null," +
                    "exchange_code varchar(32)," +
                    "market_day varchar(16) not null," +
                    "open_price decimal(18,4) not null," +
                    "high_price decimal(18,4) not null," +
                    "low_price decimal(18,4) not null," +
                    "close_price decimal(18,4) not null," +
                    "volume_qty bigint not null," +
                    "turnover_amount decimal(18,2) not null," +
                    "clean_batch varchar(64) not null," +
                    "created_at varchar(32) not null)");
            executeSql("create table if not exists clean_trade (" +
                    "global_id bigint primary key," +
                    "source_system varchar(64) not null," +
                    "source_type varchar(32) not null," +
                    "source_row_id bigint not null," +
                    "vendor_trade_no varchar(64) not null," +
                    "biz_type varchar(64) not null," +
                    "direction varchar(32) not null," +
                    "amount decimal(18,2) not null," +
                    "status_name varchar(64) not null," +
                    "counterparty_name varchar(128)," +
                    "clean_mode varchar(32) not null," +
                    "clean_batch varchar(64) not null," +
                    "trade_time varchar(32) not null," +
                    "created_at varchar(32) not null)");
            executeSql("create table if not exists clean_position (" +
                    "global_id bigint primary key," +
                    "source_system varchar(64) not null," +
                    "source_type varchar(32) not null," +
                    "source_row_id bigint not null," +
                    "account_name varchar(128) not null," +
                    "stock_code varchar(32) not null," +
                    "holding_qty bigint not null," +
                    "available_qty bigint not null," +
                    "cost_price decimal(18,4) not null," +
                    "market_value decimal(18,2) not null," +
                    "stat_day varchar(16) not null," +
                    "clean_batch varchar(64) not null," +
                    "created_at varchar(32) not null)");
            executeSql("create table if not exists clean_asset (" +
                    "global_id bigint primary key," +
                    "source_system varchar(64) not null," +
                    "source_type varchar(32) not null," +
                    "source_row_id bigint not null," +
                    "account_name varchar(128) not null," +
                    "account_no varchar(64) not null," +
                    "cash_balance decimal(18,2) not null," +
                    "frozen_balance decimal(18,2) not null," +
                    "total_asset decimal(18,2) not null," +
                    "stat_day varchar(16) not null," +
                    "clean_batch varchar(64) not null," +
                    "created_at varchar(32) not null)");
            executeSql("create table if not exists event_message (" +
                    "message_id bigint primary key," +
                    "topic varchar(64) not null," +
                    "biz_key varchar(128) not null," +
                    "payload text not null," +
                    "status varchar(32) not null," +
                    "created_at varchar(32) not null)");
            executeSql("create table if not exists tx_coordination_log (" +
                    "id bigint primary key," +
                    "source_system varchar(64) not null," +
                    "phase varchar(32) not null," +
                    "detail varchar(256) not null," +
                    "created_at varchar(32) not null)");
            executeSql("create table if not exists init_task (" +
                    "id bigint not null auto_increment primary key," +
                    "task_id varchar(64) not null," +
                    "status varchar(32) not null default 'IDLE'," +
                    "submitted_at varchar(32)," +
                    "started_at varchar(32)," +
                    "finished_at varchar(32)," +
                    "progress int default 0," +
                    "message varchar(256)," +
                    "error_message varchar(1024)," +
                    "result text," +
                    "unique key uk_init_task_id(task_id))");
            executeSql("create table if not exists sync_task (" +
                    "id bigint not null auto_increment primary key," +
                    "task_id varchar(64) not null," +
                    "data_source_key varchar(64)," +
                    "data_source_name varchar(128)," +
                    "datasource_type varchar(32)," +
                    "page_size int default 2," +
                    "status varchar(32) not null default 'IDLE'," +
                    "progress int default 0," +
                    "total_pulled_count int default 0," +
                    "total_saved_count int default 0," +
                    "submitted_at varchar(32)," +
                    "started_at varchar(32)," +
                    "finished_at varchar(32)," +
                    "message varchar(256)," +
                    "error_message varchar(1024)," +
                    "unique key uk_sync_task_id(task_id))");
            executeSql("create table if not exists sync_business_record (" +
                    "id bigint not null auto_increment primary key," +
                    "task_id varchar(64) not null," +
                    "business_code varchar(32) not null," +
                    "status varchar(32) not null default 'RUNNING'," +
                    "page_count int default 0," +
                    "pulled_count int default 0," +
                    "saved_count int default 0," +
                    "last_row_id bigint default 0," +
                    "error_message varchar(1024)," +
                    "started_at varchar(32)," +
                    "finished_at varchar(32)," +
                    "key idx_record_task_id(task_id))");
            // 确保已存在的 init_task 表有 progress 字段（MySQL 不支持 IF NOT EXISTS，忽略重复列错误）
            safeAddColumn("init_task", "progress int default 0");
            safeAddColumn("sync_task", "progress int default 0");
        });
    }

    private void createOmsTradeTables() {
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            executeSql("create table if not exists oms_stock_snapshot (" +
                    "id bigint not null auto_increment primary key," +
                    "symbol varchar(16) not null," +
                    "exchange_code varchar(32)," +
                    "market_day varchar(16) not null," +
                    "open_price decimal(18,4) not null," +
                    "high_price decimal(18,4) not null," +
                    "low_price decimal(18,4) not null," +
                    "close_price decimal(18,4) not null," +
                    "volume_qty bigint not null," +
                    "turnover_amount decimal(18,2) not null," +
                    "sync_flag int default 0 not null," +
                    "unique key uk_oms_symbol_day(symbol, market_day))");
            executeSql("create table if not exists oms_trade_order (" +
                    "id bigint not null auto_increment primary key," +
                    "order_no varchar(64) not null," +
                    "stock_code varchar(16)," +
                    "investor_name varchar(64) not null," +
                    "side_code varchar(8) not null," +
                    "trade_qty bigint default 0 not null," +
                    "trade_price decimal(18,4) default 0 not null," +
                    "order_amount decimal(18,2) not null," +
                    "trade_status varchar(32) not null," +
                    "trade_time varchar(32) not null," +
                    "sync_flag int default 0 not null)");
            executeSql("create table if not exists oms_position_holding (" +
                    "id bigint not null auto_increment primary key," +
                    "investor_name varchar(64) not null," +
                    "stock_code varchar(16) not null," +
                    "holding_qty bigint not null," +
                    "available_qty bigint not null," +
                    "cost_price decimal(18,4) not null," +
                    "market_value decimal(18,2) not null," +
                    "stat_day varchar(16) not null," +
                    "sync_flag int default 0 not null)");
            executeSql("create table if not exists oms_cash_asset (" +
                    "id bigint not null auto_increment primary key," +
                    "investor_name varchar(64) not null," +
                    "account_no varchar(64) not null," +
                    "cash_balance decimal(18,2) not null," +
                    "frozen_balance decimal(18,2) not null," +
                    "total_asset decimal(18,2) not null," +
                    "stat_day varchar(16) not null," +
                    "sync_flag int default 0 not null)");
        });
    }

    private void createBrokerTradeTables() {
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            executeSql("create table if not exists broker_stock_quote (" +
                    "id bigint not null auto_increment primary key," +
                    "quote_code varchar(64) not null," +
                    "secu_code varchar(16) not null," +
                    "trade_day varchar(16) not null," +
                    "exchange_name varchar(32)," +
                    "open_px decimal(18,4) not null," +
                    "high_px decimal(18,4) not null," +
                    "low_px decimal(18,4) not null," +
                    "close_px decimal(18,4) not null," +
                    "vol_num bigint not null," +
                    "turnover_amt decimal(18,2) not null," +
                    "sync_flag int default 0 not null," +
                    "unique key uk_broker_quote(quote_code))");
            executeSql("create table if not exists broker_trade_deal (" +
                    "id bigint not null auto_increment primary key," +
                    "deal_code varchar(64) not null," +
                    "secu_code varchar(16)," +
                    "client_full_name varchar(64) not null," +
                    "bs_flag varchar(8) not null," +
                    "deal_volume bigint default 0 not null," +
                    "deal_price decimal(18,4) default 0 not null," +
                    "turnover_amount decimal(18,2) not null," +
                    "status_mark varchar(32) not null," +
                    "deal_at varchar(32) not null," +
                    "sync_flag int default 0 not null)");
            executeSql("create table if not exists broker_position_balance (" +
                    "id bigint not null auto_increment primary key," +
                    "client_full_name varchar(64) not null," +
                    "secu_code varchar(16) not null," +
                    "current_volume bigint not null," +
                    "enable_volume bigint not null," +
                    "cost_px decimal(18,4) not null," +
                    "market_amt decimal(18,2) not null," +
                    "biz_date varchar(16) not null," +
                    "sync_flag int default 0 not null)");
            executeSql("create table if not exists broker_fund_account (" +
                    "id bigint not null auto_increment primary key," +
                    "client_full_name varchar(64) not null," +
                    "fund_account_no varchar(64) not null," +
                    "current_balance decimal(18,2) not null," +
                    "frozen_capital decimal(18,2) not null," +
                    "total_asset decimal(18,2) not null," +
                    "biz_date varchar(16) not null," +
                    "sync_flag int default 0 not null)");
        });
    }

    private void insertOmsStockSnapshot(StockSnapshot snapshot, int index) {
        OmsStockSnapshot entity = new OmsStockSnapshot();
        entity.setSymbol(snapshot.getSymbol());
        entity.setExchangeCode(defaultExchange(snapshot));
        entity.setMarketDay(normalizeTradeDay(snapshot.getDate()));
        entity.setOpenPrice(resolvePrice(snapshot.getOpen()));
        entity.setHighPrice(resolvePrice(snapshot.getHigh()));
        entity.setLowPrice(resolvePrice(snapshot.getLow()));
        entity.setClosePrice(resolvePrice(snapshot.getClose()));
        entity.setVolumeQty(resolveVolume(snapshot, index));
        entity.setTurnoverAmount(resolveTurnover(snapshot, resolveVolume(snapshot, index)));
        entity.setSyncFlag(0);
        omsStockSnapshotMapper.insert(entity);
    }

    private void insertOmsTradeSeed(StockSnapshot snapshot, int index, int repeat) {
        long qty = resolveTradeQty(snapshot, index, repeat);
        BigDecimal price = resolveTradePrice(snapshot, repeat);
        OmsTradeOrder entity = new OmsTradeOrder();
        entity.setOrderNo("OMS-" + snapshot.getSymbol() + "-" + String.format("%05d", index) + "-" + repeat);
        entity.setStockCode(snapshot.getSymbol());
        entity.setInvestorName(OMS_ACCOUNTS.get((index + repeat) % OMS_ACCOUNTS.size()));
        entity.setSideCode(repeat % 2 == 0 ? "S" : "B");
        entity.setTradeQty(qty);
        entity.setTradePrice(price);
        entity.setOrderAmount(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        entity.setTradeStatus(OMS_STATUSES.get((index + repeat) % OMS_STATUSES.size()));
        entity.setTradeTime(normalizeTradeTime(snapshot.getDate()));
        entity.setSyncFlag(0);
        omsTradeOrderMapper.insert(entity);
    }

    private void insertOmsPositionSeed(StockSnapshot snapshot, int index) {
        long holdingQty = resolvePositionQty(snapshot, index);
        BigDecimal price = resolveTradePrice(snapshot, 1);
        OmsPositionHolding entity = new OmsPositionHolding();
        entity.setInvestorName(OMS_ACCOUNTS.get(index % OMS_ACCOUNTS.size()));
        entity.setStockCode(snapshot.getSymbol());
        entity.setHoldingQty(holdingQty);
        entity.setAvailableQty(Math.max(100L, holdingQty - 100L));
        entity.setCostPrice(price);
        entity.setMarketValue(price.multiply(BigDecimal.valueOf(holdingQty)).setScale(2, RoundingMode.HALF_UP));
        entity.setStatDay(normalizeTradeDay(snapshot.getDate()));
        entity.setSyncFlag(0);
        omsPositionHoldingMapper.insert(entity);
    }

    private void insertOmsCashSeed(StockSnapshot snapshot, int index) {
        BigDecimal base = resolveTradePrice(snapshot, 2).multiply(BigDecimal.valueOf(10000L + index * 10L));
        OmsCashAsset entity = new OmsCashAsset();
        entity.setInvestorName(OMS_ACCOUNTS.get(index % OMS_ACCOUNTS.size()));
        entity.setAccountNo("OMS-ACCT-" + String.format("%04d", index));
        entity.setCashBalance(base.setScale(2, RoundingMode.HALF_UP));
        entity.setFrozenBalance(base.multiply(BigDecimal.valueOf(0.08)).setScale(2, RoundingMode.HALF_UP));
        entity.setTotalAsset(base.multiply(BigDecimal.valueOf(1.75)).setScale(2, RoundingMode.HALF_UP));
        entity.setStatDay(normalizeTradeDay(snapshot.getDate()));
        entity.setSyncFlag(0);
        omsCashAssetMapper.insert(entity);
    }

    private void insertBrokerStockQuote(StockSnapshot snapshot, int index) {
        long volume = resolveVolume(snapshot, index + 17);
        BrokerStockQuote entity = new BrokerStockQuote();
        entity.setQuoteCode(snapshot.getSymbol() + "-" + normalizeTradeDay(snapshot.getDate()));
        entity.setSecuCode(snapshot.getSymbol());
        entity.setTradeDay(normalizeTradeDay(snapshot.getDate()));
        entity.setExchangeName(defaultExchange(snapshot));
        entity.setOpenPx(resolvePrice(snapshot.getOpen()));
        entity.setHighPx(resolvePrice(snapshot.getHigh()));
        entity.setLowPx(resolvePrice(snapshot.getLow()));
        entity.setClosePx(resolvePrice(snapshot.getClose()));
        entity.setVolNum(volume);
        entity.setTurnoverAmt(resolveTurnover(snapshot, volume));
        entity.setSyncFlag(0);
        brokerStockQuoteMapper.insert(entity);
    }

    private void insertBrokerTradeSeed(StockSnapshot snapshot, int index, int repeat) {
        long qty = resolveTradeQty(snapshot, index + 11, repeat + 1);
        BigDecimal price = resolveTradePrice(snapshot, repeat + 1);
        BrokerTradeDeal entity = new BrokerTradeDeal();
        entity.setDealCode("BRK-" + snapshot.getSymbol() + "-" + String.format("%05d", index) + "-" + repeat);
        entity.setSecuCode(snapshot.getSymbol());
        entity.setClientFullName(BROKER_CLIENTS.get((index + repeat) % BROKER_CLIENTS.size()));
        entity.setBsFlag(repeat % 2 == 0 ? "2" : "1");
        entity.setDealVolume(qty);
        entity.setDealPrice(price);
        entity.setTurnoverAmount(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        entity.setStatusMark(BROKER_STATUSES.get((index + repeat) % BROKER_STATUSES.size()));
        entity.setDealAt(normalizeTradeTime(snapshot.getDate()));
        entity.setSyncFlag(0);
        brokerTradeDealMapper.insert(entity);
    }

    private void insertBrokerPositionSeed(StockSnapshot snapshot, int index) {
        long qty = resolvePositionQty(snapshot, index + 5);
        BigDecimal price = resolveTradePrice(snapshot, 2);
        BrokerPositionBalance entity = new BrokerPositionBalance();
        entity.setClientFullName(BROKER_CLIENTS.get(index % BROKER_CLIENTS.size()));
        entity.setSecuCode(snapshot.getSymbol());
        entity.setCurrentVolume(qty);
        entity.setEnableVolume(Math.max(100L, qty - 200L));
        entity.setCostPx(price);
        entity.setMarketAmt(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        entity.setBizDate(normalizeTradeDay(snapshot.getDate()));
        entity.setSyncFlag(0);
        brokerPositionBalanceMapper.insert(entity);
    }

    private void insertBrokerFundSeed(StockSnapshot snapshot, int index) {
        BigDecimal base = resolveTradePrice(snapshot, 3).multiply(BigDecimal.valueOf(12000L + index * 12L));
        BrokerFundAccount entity = new BrokerFundAccount();
        entity.setClientFullName(BROKER_CLIENTS.get(index % BROKER_CLIENTS.size()));
        entity.setFundAccountNo("FUND-" + String.format("%04d", index));
        entity.setCurrentBalance(base.setScale(2, RoundingMode.HALF_UP));
        entity.setFrozenCapital(base.multiply(BigDecimal.valueOf(0.06)).setScale(2, RoundingMode.HALF_UP));
        entity.setTotalAsset(base.multiply(BigDecimal.valueOf(1.68)).setScale(2, RoundingMode.HALF_UP));
        entity.setBizDate(normalizeTradeDay(snapshot.getDate()));
        entity.setSyncFlag(0);
        brokerFundAccountMapper.insert(entity);
    }

    private long resolveTradeQty(StockSnapshot snapshot, int index, int repeat) {
        long base = resolveVolume(snapshot, index) % 5000L;
        return ((base / 100L) + 5L + repeat) * 100L;
    }

    private long resolvePositionQty(StockSnapshot snapshot, int index) {
        long base = resolveVolume(snapshot, index) % 8000L;
        return ((base / 100L) + 10L) * 100L;
    }

    private long resolveVolume(StockSnapshot snapshot, int index) {
        if (snapshot.getVolume() == null) {
            return 50000L + index * 500L;
        }
        long volume = snapshot.getVolume().longValue();
        return Math.max(1000L, volume);
    }

    private BigDecimal resolveTradePrice(StockSnapshot snapshot, int bias) {
        BigDecimal basePrice = resolveBasePrice(snapshot);
        return basePrice.add(BigDecimal.valueOf(bias).multiply(BigDecimal.valueOf(0.03))).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveBasePrice(StockSnapshot snapshot) {
        if (snapshot.getClose() != null) {
            return snapshot.getClose();
        }
        if (snapshot.getOpen() != null) {
            return snapshot.getOpen();
        }
        return BigDecimal.valueOf(100);
    }

    private BigDecimal resolvePrice(BigDecimal price) {
        return (price == null ? BigDecimal.valueOf(100) : price).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveTurnover(StockSnapshot snapshot, long volume) {
        return resolveBasePrice(snapshot).multiply(BigDecimal.valueOf(volume)).setScale(2, RoundingMode.HALF_UP);
    }

    private String defaultExchange(StockSnapshot snapshot) {
        return snapshot.getExchange() == null || snapshot.getExchange().isBlank() ? "XNAS" : snapshot.getExchange();
    }

    private String normalizeTradeTime(String marketstackDate) {
        if (marketstackDate == null || marketstackDate.isBlank()) {
            return LocalDateTime.now().format(FORMATTER);
        }
        String normalized = marketstackDate.replace('T', ' ');
        return normalized.length() >= 19 ? normalized.substring(0, 19) : normalized;
    }

    private String normalizeTradeDay(String marketstackDate) {
        if (marketstackDate == null || marketstackDate.isBlank()) {
            return LocalDate.now().toString();
        }
        return marketstackDate.length() >= 10 ? marketstackDate.substring(0, 10) : marketstackDate;
    }

    private Map<String, Integer> countTables(String dataSourceKey, List<String> tables) {
        return routingMybatisExecutor.query(dataSourceKey, () -> {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (String table : tables) {
                Integer count = dynamicSqlMapper.countTable(table);
                result.put(table, count == null ? 0 : count);
            }
            return result;
        });
    }

    private void executeSql(String sql) {
        dynamicSqlMapper.executeSql(sql);
    }

    /** MySQL 兼容的添加字段 — IF NOT EXISTS 非 MySQL 标准语法，用 try-catch 忽略重复列错误 */
    private void safeAddColumn(String table, String columnDef) {
        try {
            executeSql("ALTER TABLE " + table + " ADD COLUMN " + columnDef);
        } catch (Exception e) {
            log.debug("[Schema] 忽略已存在列: {}.{}", table, columnDef.substring(0, columnDef.indexOf(' ')));
        }
    }

    private DictItem buildDictItem(String dictType, String dictCode, String dictName, String dictDesc) {
        DictItem item = new DictItem();
        item.setDictType(dictType);
        item.setDictCode(dictCode);
        item.setDictName(dictName);
        item.setDictDesc(dictDesc);
        return item;
    }

    private LeafAlloc buildLeafAlloc(String bizTag, Long maxId, Integer step, String description) {
        LeafAlloc alloc = new LeafAlloc();
        alloc.setBizTag(bizTag);
        alloc.setMaxId(maxId);
        alloc.setStep(step);
        alloc.setDescription(description);
        return alloc;
    }

    // ========== 本地股票数据生成（替代原 Marketstack 远程拉取） ==========

    private static final List<String> SYMBOLS = List.of(
            "AAPL", "MSFT", "NVDA", "AMZN", "META",
            "TSLA", "GOOGL", "AMD", "NFLX", "INTC",
            "ORCL", "IBM", "JPM", "BAC", "WMT",
            "TSM", "QCOM", "AVGO", "ADBE", "CRM",
            "CSCO", "UBER", "PYPL", "SHOP", "SQ",
            "COIN", "MU", "ARM", "SONY", "BABA",
            "PDD", "BIDU", "NIO", "XPEV", "LI",
            "DIS", "KO", "MCD", "PEP", "COST", "HD");

    /** 回溯天数 */
    private static final int LOOKBACK_DAYS = 120;
    /** 最大生成行数 */
    private static final int MAX_FALLBACK_ROWS = 2000;

    /**
     * 生成本地兜底股票行情数据（伪随机 OHLCV）。
     * 数据范围：从今天往前推 LOOKBACK_DAYS 天，覆盖 SYMBOLS 中所有股票。
     */
    private List<StockSnapshot> generateFallbackStocks() {
        List<StockSnapshot> result = new ArrayList<>();
        LocalDate dateTo = LocalDate.now();
        LocalDate dateFrom = dateTo.minusDays(LOOKBACK_DAYS);

        long totalDays = Math.max(1L, dateTo.toEpochDay() - dateFrom.toEpochDay() + 1L);
        log.info("[平台初始化] 开始生成本地股票数据 symbolsCount={}, totalDays={}, maxRows={}",
                SYMBOLS.size(), totalDays, MAX_FALLBACK_ROWS);

        for (int dayOffset = 0; dayOffset < totalDays && result.size() < MAX_FALLBACK_ROWS; dayOffset++) {
            LocalDate tradeDate = dateTo.minusDays(dayOffset);
            for (int symbolIndex = 0; symbolIndex < SYMBOLS.size() && result.size() < MAX_FALLBACK_ROWS; symbolIndex++) {
                result.add(buildFallbackSnapshot(SYMBOLS.get(symbolIndex), tradeDate, symbolIndex, dayOffset));
            }
        }

        result.sort(Comparator.comparing(StockSnapshot::getDate).reversed()
                .thenComparing(StockSnapshot::getSymbol));
        log.info("[平台初始化] 本地股票数据生成完成 count={}", result.size());
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

    /**
     * 股票快照数据 — 用于初始化时生成伪随机行情。
     * 替代原 MarketstackService.StockSnapshot。
     */
    @lombok.Data
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

    /**
     * 从 JDBC URL 中提取数据库名称（最后一个 '/' 之后、'?' 之前的部分）
     * 例：jdbc:mysql://host:3306/risk_hub?useSSL=false → risk_hub
     */
    private String extractSchemaName(String jdbcUrl) {
        int queryIndex = jdbcUrl.indexOf('?');
        String base = queryIndex >= 0 ? jdbcUrl.substring(0, queryIndex) : jdbcUrl;
        int start = base.lastIndexOf('/') + 1;
        if (start <= 0 || start >= base.length()) {
            throw new IllegalArgumentException("无法从 JDBC URL 提取 schema: " + jdbcUrl);
        }
        return base.substring(start);
    }

    private String toBootstrapUrl(String originalUrl) {
        int queryIndex = originalUrl.indexOf('?');
        String base = queryIndex >= 0 ? originalUrl.substring(0, queryIndex) : originalUrl;
        String query = queryIndex >= 0 ? originalUrl.substring(queryIndex) : "";
        int slashAfterHost = base.indexOf('/', "jdbc:mysql://".length());
        if (slashAfterHost < 0) {
            return base + "/" + query;
        }
        return base.substring(0, slashAfterHost) + "/" + query;
    }
}
