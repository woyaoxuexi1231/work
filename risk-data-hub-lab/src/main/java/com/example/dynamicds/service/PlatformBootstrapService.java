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
import org.springframework.beans.factory.annotation.Value;
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

@Slf4j
@Service
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

    // Leaf biz tags
    private static final String TAG_OMS_SNAPSHOT = "oms_stock_snapshot";
    private static final String TAG_OMS_ORDER = "oms_trade_order";
    private static final String TAG_OMS_POSITION = "oms_position_holding";
    private static final String TAG_OMS_CASH = "oms_cash_asset";
    private static final String TAG_BROKER_QUOTE = "broker_stock_quote";
    private static final String TAG_BROKER_DEAL = "broker_trade_deal";
    private static final String TAG_BROKER_POSITION = "broker_position_balance";
    private static final String TAG_BROKER_FUND = "broker_fund_account";
    private static final String TAG_DICT_ITEM = "dict_item";

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

    @Value("${spring.datasource.url}")
    private String hubUrl;

    @Value("${app.ddl.enabled:true}")
    private boolean ddlEnabled;

    @PostConstruct
    public void init() {
        log.info("[平台初始化] ddl.enabled={}", ddlEnabled);
        manager.putHubConfig(DS_HUB, "中台库", hubUrl);
        ensureTradeSchemas();
        ensureDataSource(DS_TRADE_OMS);
        ensureDataSource(DS_TRADE_BROKER);
        if (ddlEnabled) {
            ensureLatestSchema();
        }
        leafSegmentService.clearLocalCache();
        log.info("[平台初始化] 完成");
    }

    /** 仅创建 trade 业务的 schema（hub 由 spring.datasource.url 保证已存在） */
    private void ensureTradeSchemas() {
        for (HubDataSourceProperties.Item item : properties.getItems()) {
            try {
                String url = item.getUrl();
                String schemaName = url.substring(url.lastIndexOf('/') + 1);
                String user = item.getUsername();
                String pwd = item.getPassword();
                try (Connection conn = DriverManager.getConnection(
                        url.substring(0, url.lastIndexOf('/')), user, pwd);
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("create database if not exists `" + schemaName + "` default character set utf8mb4");
                    log.info("[平台初始化] schema={}", schemaName);
                }
            } catch (Exception e) {
                throw new IllegalStateException("auto-create schema failed: " + e.getMessage(), e);
            }
        }
    }

    // ============ DDL ============

    private void ensureLatestSchema() {
        createHubTables();
        createOmsTradeTables();
        createBrokerTradeTables();
    }

    private void createHubTables() {
        routingMybatisExecutor.run(DS_HUB, () -> {
            executeSql("create table if not exists dict_item (" +
                    "id bigint primary key," +
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
                    "id bigint primary key," +
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
                    "id bigint primary key," +
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
                    "id bigint primary key," +
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
            safeAddColumn("init_task", "progress int default 0");
            safeAddColumn("sync_task", "progress int default 0");
        });
    }

    private void createOmsTradeTables() {
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            executeSql("create table if not exists oms_stock_snapshot (" +
                    "id bigint primary key," +
                    "symbol varchar(16) not null," +
                    "exchange_code varchar(32)," +
                    "market_day varchar(16) not null," +
                    "open_px decimal(18,4)," +
                    "high_px decimal(18,4)," +
                    "low_px decimal(18,4)," +
                    "close_px decimal(18,4)," +
                    "vol_num bigint," +
                    "turnover_amt decimal(18,2)," +
                    "sync_flag int default 0)");
            executeSql("create table if not exists oms_trade_order (" +
                    "id bigint primary key," +
                    "order_no varchar(64) not null," +
                    "stock_code varchar(32) not null," +
                    "investor_name varchar(128)," +
                    "side_code varchar(8)," +
                    "trade_qty bigint," +
                    "trade_price decimal(18,4)," +
                    "order_amount decimal(18,2)," +
                    "trade_status varchar(32)," +
                    "trade_time varchar(32)," +
                    "sync_flag int default 0)");
            executeSql("create table if not exists oms_position_holding (" +
                    "id bigint primary key," +
                    "investor_name varchar(128)," +
                    "stock_code varchar(32) not null," +
                    "holding_qty bigint," +
                    "available_qty bigint," +
                    "cost_price decimal(18,4)," +
                    "market_value decimal(18,2)," +
                    "stat_day varchar(16)," +
                    "sync_flag int default 0)");
            executeSql("create table if not exists oms_cash_asset (" +
                    "id bigint primary key," +
                    "investor_name varchar(128)," +
                    "account_no varchar(64)," +
                    "cash_balance decimal(18,2)," +
                    "frozen_balance decimal(18,2)," +
                    "total_asset decimal(18,2)," +
                    "stat_day varchar(16)," +
                    "sync_flag int default 0)");
        });
    }

    private void createBrokerTradeTables() {
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            executeSql("create table if not exists broker_stock_quote (" +
                    "id bigint primary key," +
                    "quote_code varchar(64) not null," +
                    "secu_code varchar(32) not null," +
                    "trade_day varchar(16)," +
                    "exchange_name varchar(32)," +
                    "open_px decimal(18,4)," +
                    "high_px decimal(18,4)," +
                    "low_px decimal(18,4)," +
                    "close_px decimal(18,4)," +
                    "vol_num bigint," +
                    "turnover_amt decimal(18,2)," +
                    "sync_flag int default 0)");
            executeSql("create table if not exists broker_trade_deal (" +
                    "id bigint primary key," +
                    "deal_code varchar(64) not null," +
                    "secu_code varchar(32) not null," +
                    "client_full_name varchar(128)," +
                    "bs_flag varchar(4)," +
                    "deal_volume bigint," +
                    "deal_price decimal(18,4)," +
                    "turnover_amount decimal(18,2)," +
                    "status_mark varchar(4)," +
                    "deal_at varchar(32)," +
                    "sync_flag int default 0)");
            executeSql("create table if not exists broker_position_balance (" +
                    "id bigint primary key," +
                    "client_full_name varchar(128)," +
                    "secu_code varchar(32) not null," +
                    "current_volume bigint," +
                    "enable_volume bigint," +
                    "cost_px decimal(18,4)," +
                    "market_amt decimal(18,2)," +
                    "biz_date varchar(16)," +
                    "sync_flag int default 0)");
            executeSql("create table if not exists broker_fund_account (" +
                    "id bigint primary key," +
                    "client_full_name varchar(128)," +
                    "account_no varchar(64)," +
                    "cash_balance decimal(18,2)," +
                    "frozen_amt decimal(18,2)," +
                    "total_asset decimal(18,2)," +
                    "stat_day varchar(16)," +
                    "sync_flag int default 0)");
        });
    }

    // ============ 数据源注册 ============

    private void ensureDataSource(String key) {
        if (manager.exists(key)) return;
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

    // ============ 初始化 demo 数据 ============

    public synchronized Map<String, Object> initDemoData() {
        log.info("[平台初始化] 开始初始化演示数据");
        clearBusinessDataTables();
        initHubBaseData();
        List<StockSnapshot> snapshots = generateFallbackStocks();
        seedTradeSystemsFromMarketData(snapshots);
        leafSegmentService.clearLocalCache();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotCount", snapshots.size());
        result.put("businessTableStats", currentBusinessTableStats());
        result.put("hubTableStats", currentHubTableStats());
        log.info("[平台初始化] 完成, snapshotCount={}", snapshots.size());
        return result;
    }

    public synchronized Map<String, Object> initDemoDataWithProgress(IntConsumer progressCallback) {
        log.info("[平台初始化] 开始初始化演示数据（带进度）");
        progressCallback.accept(0);
        clearBusinessDataTables();
        progressCallback.accept(3);
        initHubBaseData();
        progressCallback.accept(5);

        List<StockSnapshot> snapshots = generateFallbackStocks();
        progressCallback.accept(15);

        seedTradeSystemsFromMarketDataWithProgress(snapshots, progressCallback, 15, 60);
        seedBrokerSystemsFromMarketDataWithProgress(snapshots, progressCallback, 60, 95);

        leafSegmentService.clearLocalCache();
        progressCallback.accept(98);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotCount", snapshots.size());
        result.put("businessTableStats", currentBusinessTableStats());
        result.put("hubTableStats", currentHubTableStats());
        progressCallback.accept(100);
        log.info("[平台初始化] 完成, snapshotCount={}", snapshots.size());
        return result;
    }

    // ============ 清空业务数据（不动运营表） ============

    private void clearBusinessDataTables() {
        clearOmsTradeTables();
        clearBrokerTradeTables();
        clearCleanTables();
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

    private void clearCleanTables() {
        routingMybatisExecutor.run(DS_HUB, () -> {
            executeSql("truncate table clean_asset");
            executeSql("truncate table clean_position");
            executeSql("truncate table clean_trade");
            executeSql("truncate table clean_stock");
            executeSql("truncate table event_message");
            executeSql("truncate table tx_coordination_log");
            executeSql("truncate table dict_item");
            executeSql("truncate table leaf_alloc");
        });
    }

    // ============ Hub 基础数据（字典 + 发号器） ============

    private void initHubBaseData() {
        routingMybatisExecutor.run(DS_HUB, () -> {
            dictItemMapper.insert(buildDictItem("trade_status_oms", "NEW", "待确认", "交易系统A待确认状态"));
            dictItemMapper.insert(buildDictItem("trade_status_oms", "DONE", "已成交", "交易系统A成交完成"));
            dictItemMapper.insert(buildDictItem("trade_status_oms", "CANCEL", "已撤单", "交易系统A撤单状态"));
            dictItemMapper.insert(buildDictItem("trade_status_broker", "A", "待确认", "交易系统B待确认状态"));
            dictItemMapper.insert(buildDictItem("trade_status_broker", "S", "已成交", "交易系统B成交完成"));
            dictItemMapper.insert(buildDictItem("trade_status_broker", "X", "已撤单", "交易系统B撤单状态"));

            leafAllocMapper.insert(buildLeafAlloc(TAG_DICT_ITEM, 1L, 20, "字典项主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_OMS_SNAPSHOT, 1L, 20, "OMS股票快照主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_OMS_ORDER, 1L, 20, "OMS交易订单主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_OMS_POSITION, 1L, 20, "OMS持仓主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_OMS_CASH, 1L, 20, "OMS资金主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_BROKER_QUOTE, 1L, 20, "券商行情主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_BROKER_DEAL, 1L, 20, "券商成交主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_BROKER_POSITION, 1L, 20, "券商持仓主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_BROKER_FUND, 1L, 20, "券商资金主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_stock", 50000L, 20, "中台标准股票主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_trade", 100000L, 20, "中台标准交易主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_position", 200000L, 20, "中台标准持仓主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_asset", 300000L, 20, "中台标准资金主键"));
            leafAllocMapper.insert(buildLeafAlloc("event_message", 500000L, 20, "同步事件主键"));
            leafAllocMapper.insert(buildLeafAlloc("tx_audit", 900000L, 10, "事务审计主键"));
        });
    }

    // ============ 灌数据 ============

    private void seedTradeSystemsFromMarketData(List<StockSnapshot> snapshots) {
        log.info("[平台初始化] 写入交易系统A, size={}", snapshots.size());
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            for (int i = 0; i < snapshots.size(); i++) {
                StockSnapshot s = snapshots.get(i);
                insertOmsStockSnapshot(s);
                for (int j = 0; j < OMS_ORDER_REPEAT; j++) insertOmsTradeSeed(s, j);
                insertOmsPositionSeed(s);
                if (i % 3 == 0) insertOmsCashSeed(s);
                logBootstrapProgress("交易系统A", i + 1, snapshots.size());
            }
        });
        log.info("[平台初始化] 写入交易系统B, size={}", snapshots.size());
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            for (int i = 0; i < snapshots.size(); i++) {
                StockSnapshot s = snapshots.get(i);
                insertBrokerStockQuote(s);
                for (int j = 0; j < BROKER_DEAL_REPEAT; j++) insertBrokerTradeSeed(s, j);
                insertBrokerPositionSeed(s);
                if (i % 3 == 0) insertBrokerFundSeed(s);
                logBootstrapProgress("交易系统B", i + 1, snapshots.size());
            }
        });
    }

    private void seedTradeSystemsFromMarketDataWithProgress(List<StockSnapshot> snapshots,
                                                            IntConsumer pc, int ps, int pe) {
        int total = snapshots.size();
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            for (int i = 0; i < total; i++) {
                StockSnapshot s = snapshots.get(i);
                insertOmsStockSnapshot(s);
                for (int j = 0; j < OMS_ORDER_REPEAT; j++) insertOmsTradeSeed(s, j);
                insertOmsPositionSeed(s);
                if (i % 3 == 0) insertOmsCashSeed(s);
                pc.accept(ps + (int) ((i + 1) / (double) total * (pe - ps)));
            }
        });
        seedBrokerSystemsFromMarketDataWithProgress(snapshots, pc, pe, pe + (pe - ps) / 2);
    }

    private void seedBrokerSystemsFromMarketDataWithProgress(List<StockSnapshot> snapshots,
                                                             IntConsumer pc, int ps, int pe) {
        int total = snapshots.size();
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            for (int i = 0; i < total; i++) {
                StockSnapshot s = snapshots.get(i);
                insertBrokerStockQuote(s);
                for (int j = 0; j < BROKER_DEAL_REPEAT; j++) insertBrokerTradeSeed(s, j);
                insertBrokerPositionSeed(s);
                if (i % 3 == 0) insertBrokerFundSeed(s);
                pc.accept(ps + (int) ((i + 1) / (double) total * (pe - ps)));
            }
        });
    }

    // ============ 插入方法（全走 Leaf 发号器） ============

    private void insertOmsStockSnapshot(StockSnapshot snapshot) {
        OmsStockSnapshot e = new OmsStockSnapshot();
        e.setId(leafSegmentService.nextId(TAG_OMS_SNAPSHOT));
        e.setSymbol(snapshot.getSymbol());
        e.setExchangeCode(defaultExchange(snapshot));
        e.setMarketDay(normalizeTradeDay(snapshot.getDate()));
        e.setOpenPrice(resolvePrice(snapshot.getOpen()));
        e.setHighPrice(resolvePrice(snapshot.getHigh()));
        e.setLowPrice(resolvePrice(snapshot.getLow()));
        e.setClosePrice(resolvePrice(snapshot.getClose()));
        e.setVolumeQty(resolveVolume(snapshot, 0));
        e.setTurnoverAmount(resolveTurnover(snapshot, resolveVolume(snapshot, 0)));
        e.setSyncFlag(0);
        omsStockSnapshotMapper.insert(e);
    }

    private void insertOmsTradeSeed(StockSnapshot snapshot, int repeat) {
        long qty = resolveTradeQty(snapshot, 0, repeat);
        BigDecimal price = resolveTradePrice(snapshot, repeat);
        OmsTradeOrder e = new OmsTradeOrder();
        e.setId(leafSegmentService.nextId(TAG_OMS_ORDER));
        e.setOrderNo("OMS-" + snapshot.getSymbol() + "-" + String.format("%05d", repeat) + "-" + repeat);
        e.setStockCode(snapshot.getSymbol());
        e.setInvestorName(OMS_ACCOUNTS.get(repeat % OMS_ACCOUNTS.size()));
        e.setSideCode(repeat % 2 == 0 ? "S" : "B");
        e.setTradeQty(qty);
        e.setTradePrice(price);
        e.setOrderAmount(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        e.setTradeStatus(OMS_STATUSES.get(repeat % OMS_STATUSES.size()));
        e.setTradeTime(normalizeTradeTime(snapshot.getDate()));
        e.setSyncFlag(0);
        omsTradeOrderMapper.insert(e);
    }

    private void insertOmsPositionSeed(StockSnapshot snapshot) {
        long qty = resolvePositionQty(snapshot, 0);
        BigDecimal price = resolveTradePrice(snapshot, 1);
        OmsPositionHolding e = new OmsPositionHolding();
        e.setId(leafSegmentService.nextId(TAG_OMS_POSITION));
        e.setInvestorName(OMS_ACCOUNTS.get(0));
        e.setStockCode(snapshot.getSymbol());
        e.setHoldingQty(qty);
        e.setAvailableQty(Math.max(100L, qty - 100L));
        e.setCostPrice(price);
        e.setMarketValue(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        e.setStatDay(normalizeTradeDay(snapshot.getDate()));
        e.setSyncFlag(0);
        omsPositionHoldingMapper.insert(e);
    }

    private void insertOmsCashSeed(StockSnapshot snapshot) {
        BigDecimal base = resolveTradePrice(snapshot, 2).multiply(BigDecimal.valueOf(10000));
        OmsCashAsset e = new OmsCashAsset();
        e.setId(leafSegmentService.nextId(TAG_OMS_CASH));
        e.setInvestorName(OMS_ACCOUNTS.get(0));
        e.setAccountNo("OMS-ACCT-0001");
        e.setCashBalance(base.setScale(2, RoundingMode.HALF_UP));
        e.setFrozenBalance(base.multiply(BigDecimal.valueOf(0.08)).setScale(2, RoundingMode.HALF_UP));
        e.setTotalAsset(base.multiply(BigDecimal.valueOf(1.75)).setScale(2, RoundingMode.HALF_UP));
        e.setStatDay(normalizeTradeDay(snapshot.getDate()));
        e.setSyncFlag(0);
        omsCashAssetMapper.insert(e);
    }

    private void insertBrokerStockQuote(StockSnapshot snapshot) {
        long volume = resolveVolume(snapshot, 17);
        BrokerStockQuote e = new BrokerStockQuote();
        e.setId(leafSegmentService.nextId(TAG_BROKER_QUOTE));
        e.setQuoteCode(snapshot.getSymbol() + "-" + normalizeTradeDay(snapshot.getDate()));
        e.setSecuCode(snapshot.getSymbol());
        e.setTradeDay(normalizeTradeDay(snapshot.getDate()));
        e.setExchangeName(defaultExchange(snapshot));
        e.setOpenPx(resolvePrice(snapshot.getOpen()));
        e.setHighPx(resolvePrice(snapshot.getHigh()));
        e.setLowPx(resolvePrice(snapshot.getLow()));
        e.setClosePx(resolvePrice(snapshot.getClose()));
        e.setVolNum(volume);
        e.setTurnoverAmt(resolveTurnover(snapshot, volume));
        e.setSyncFlag(0);
        brokerStockQuoteMapper.insert(e);
    }

    private void insertBrokerTradeSeed(StockSnapshot snapshot, int repeat) {
        long qty = resolveTradeQty(snapshot, 11, repeat + 1);
        BigDecimal price = resolveTradePrice(snapshot, repeat + 1);
        BrokerTradeDeal e = new BrokerTradeDeal();
        e.setId(leafSegmentService.nextId(TAG_BROKER_DEAL));
        e.setDealCode("BRK-" + snapshot.getSymbol() + "-" + String.format("%05d", repeat) + "-" + repeat);
        e.setSecuCode(snapshot.getSymbol());
        e.setClientFullName(BROKER_CLIENTS.get(repeat % BROKER_CLIENTS.size()));
        e.setBsFlag(repeat % 2 == 0 ? "2" : "1");
        e.setDealVolume(qty);
        e.setDealPrice(price);
        e.setTurnoverAmount(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        e.setStatusMark(BROKER_STATUSES.get(repeat % BROKER_STATUSES.size()));
        e.setDealAt(normalizeTradeTime(snapshot.getDate()));
        e.setSyncFlag(0);
        brokerTradeDealMapper.insert(e);
    }

    private void insertBrokerPositionSeed(StockSnapshot snapshot) {
        long qty = resolvePositionQty(snapshot, 5);
        BigDecimal price = resolveTradePrice(snapshot, 2);
        BrokerPositionBalance e = new BrokerPositionBalance();
        e.setId(leafSegmentService.nextId(TAG_BROKER_POSITION));
        e.setClientFullName(BROKER_CLIENTS.get(0));
        e.setSecuCode(snapshot.getSymbol());
        e.setCurrentVolume(qty);
        e.setEnableVolume(Math.max(100L, qty - 200L));
        e.setCostPx(price);
        e.setMarketAmt(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        e.setBizDate(normalizeTradeDay(snapshot.getDate()));
        e.setSyncFlag(0);
        brokerPositionBalanceMapper.insert(e);
    }

    private void insertBrokerFundSeed(StockSnapshot snapshot) {
        BigDecimal base = resolveTradePrice(snapshot, 3).multiply(BigDecimal.valueOf(12000));
        BrokerFundAccount e = new BrokerFundAccount();
        e.setId(leafSegmentService.nextId(TAG_BROKER_FUND));
        e.setClientFullName(BROKER_CLIENTS.get(0));
        e.setFundAccountNo("BRK-ACCT-0001");
        e.setCurrentBalance(base.setScale(2, RoundingMode.HALF_UP));
        e.setFrozenCapital(base.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP));
        e.setTotalAsset(base.multiply(BigDecimal.valueOf(1.65)).setScale(2, RoundingMode.HALF_UP));
        e.setBizDate(normalizeTradeDay(snapshot.getDate()));
        e.setSyncFlag(0);
        brokerFundAccountMapper.insert(e);
    }

    private void logBootstrapProgress(String sys, int cur, int total) {
        if (cur == total || cur % BOOTSTRAP_PROGRESS_STEP == 0) {
            log.info("[平台初始化] {} {}/{}", sys, cur, total);
        }
    }

    // ============ 统计 ============

    public Map<String, List<String>> currentTopology() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>(manager.keys());
        sources.replaceAll(k -> {
            DataSourceConfigDTO c = manager.getConfig(k);
            return k + " (" + (c != null ? c.getName() : "?") + ")";
        });
        map.put("上游业务系统", sources);
        map.put("中台库", List.of(DS_HUB));
        return map;
    }

    public Map<String, Object> currentBusinessTableStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put(DS_TRADE_OMS, countTables(DS_TRADE_OMS, List.of("oms_stock_snapshot", "oms_trade_order", "oms_position_holding", "oms_cash_asset")));
        r.put(DS_TRADE_BROKER, countTables(DS_TRADE_BROKER, List.of("broker_stock_quote", "broker_trade_deal", "broker_position_balance", "broker_fund_account")));
        return r;
    }

    public Map<String, Integer> currentHubTableStats() {
        return countTables(DS_HUB, List.of("clean_stock", "clean_trade", "clean_position", "clean_asset", "event_message", "init_task", "sync_task", "sync_business_record"));
    }

    // ============ 工具 ============

    private void executeSql(String sql) {
        dynamicSqlMapper.executeSql(sql);
    }

    private void safeAddColumn(String table, String columnDef) {
        try {
            executeSql("ALTER TABLE " + table + " ADD COLUMN " + columnDef);
        } catch (Exception e) {
            log.debug("[Schema] 忽略已存在列: {}.{}", table, columnDef.substring(0, columnDef.indexOf(' ')));
        }
    }

    private DictItem buildDictItem(String dictType, String dictCode, String dictName, String dictDesc) {
        DictItem item = new DictItem();
        item.setId(leafSegmentService.nextId(TAG_DICT_ITEM));
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

    private Map<String, Integer> countTables(String dsKey, List<String> tables) {
        return routingMybatisExecutor.query(dsKey, () -> {
            Map<String, Integer> r = new LinkedHashMap<>();
            for (String t : tables) r.put(t, dynamicSqlMapper.countTable(t));
            return r;
        });
    }

    // ============ price/volume 工具 ============

    private static final Map<String, StockSnapshot> FALLBACK_MAP = new LinkedHashMap<>();
    static {
        FALLBACK_MAP.put("AAPL", new StockSnapshot("AAPL", "NASDAQ", LocalDate.of(2024, 12, 16), "150.00", "152.00", "149.00", "151.50", "10000000"));
        FALLBACK_MAP.put("GOOGL", new StockSnapshot("GOOGL", "NASDAQ", LocalDate.of(2024, 12, 16), "130.00", "132.00", "129.00", "131.50", "8000000"));
    }

    private List<StockSnapshot> generateFallbackStocks() {
        return new ArrayList<>(FALLBACK_MAP.values());
    }

    private BigDecimal resolvePrice(Object val) {
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (Exception e) {
            return new BigDecimal("100.00");
        }
    }

    private long resolveVolume(StockSnapshot s, int offset) {
        try {
            return Long.parseLong(s.getVolume()) + offset;
        } catch (Exception e) {
            return 1000000 + offset;
        }
    }

    private long resolveTradeQty(StockSnapshot s, int offset, int repeat) {
        return resolveVolume(s, offset) / (repeat + 1) / 100;
    }

    private long resolvePositionQty(StockSnapshot s, int offset) {
        return resolveVolume(s, offset) / 10;
    }

    private BigDecimal resolveTradePrice(StockSnapshot s, int offset) {
        BigDecimal p = resolvePrice(s.getClose());
        return p.add(BigDecimal.valueOf(offset * 0.01)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveTurnover(StockSnapshot s, long volume) {
        return resolvePrice(s.getClose()).multiply(BigDecimal.valueOf(volume)).setScale(2, RoundingMode.HALF_UP);
    }

    private String defaultExchange(StockSnapshot s) {
        return s.getExchange() != null ? s.getExchange() : "SSE";
    }

    private String normalizeTradeDay(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private String normalizeTradeTime(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 09:30:00";
    }

    // ============ 内部类 ============

    private static class StockSnapshot implements Comparable<StockSnapshot> {
        private final String symbol;
        private final String exchange;
        private final LocalDate date;
        private final String open;
        private final String high;
        private final String low;
        private final String close;
        private final String volume;

        StockSnapshot(String symbol, String exchange, LocalDate date, String open, String high, String low, String close, String volume) {
            this.symbol = symbol;
            this.exchange = exchange;
            this.date = date;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }

        public String getSymbol() { return symbol; }
        public String getExchange() { return exchange; }
        public LocalDate getDate() { return date; }
        public String getOpen() { return open; }
        public String getHigh() { return high; }
        public String getLow() { return low; }
        public String getClose() { return close; }
        public String getVolume() { return volume; }

        @Override
        public int compareTo(StockSnapshot o) { return symbol.compareTo(o.symbol); }
    }
}
