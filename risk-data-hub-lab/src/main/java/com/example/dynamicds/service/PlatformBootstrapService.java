package com.example.dynamicds.service;

import com.example.dynamicds.config.HubDataSourceProperties;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingJdbcExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final RoutingJdbcExecutor jdbcExecutor;
    private final LeafSegmentService leafSegmentService;
    private final HubDataSourceProperties properties;
    private final MarketstackService marketstackService;

    @PostConstruct
    public void init() {
        log.info("[平台初始化] 开始自动创建 schema、注册数据源并加载初始股票数据");
        ensureSchemas();
        ensureDataSource(DS_HUB);
        ensureDataSource(DS_TRADE_OMS);
        ensureDataSource(DS_TRADE_BROKER);
        resetDemoData();
        log.info("[平台初始化] schema、表结构和初始股票数据准备完成");
    }

    public synchronized void resetDemoData() {
        log.info("[平台初始化] 开始按最新结构重建表并重置演示数据");
        rebuildLatestSchema();
        initHubBaseData();
        List<MarketstackService.StockSnapshot> snapshots = marketstackService.fetchBootstrapStocks();
        seedTradeSystemsFromMarketData(snapshots);
        leafSegmentService.clearLocalCache();
        log.info("[平台初始化] 演示数据重置完成，股票基础样本条数={}, 业务表统计={}", snapshots.size(), currentBusinessTableStats());
    }

    public Map<String, Object> currentTopology() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hub", DS_HUB);
        map.put("upstreams", List.of(
                Map.of(
                        "key", DS_TRADE_OMS,
                        "type", TYPE_TRADE_OMS,
                        "syncTable", "oms_trade_order",
                        "tables", List.of("oms_stock_snapshot", "oms_trade_order", "oms_position_holding", "oms_cash_asset")),
                Map.of(
                        "key", DS_TRADE_BROKER,
                        "type", TYPE_TRADE_BROKER,
                        "syncTable", "broker_trade_deal",
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

    private void rebuildLatestSchema() {
        dropBrokerTradeTables();
        dropOmsTradeTables();
        dropHubTables();
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
        jdbcExecutor.run(DS_HUB, jdbc -> {
            jdbc.update("insert into dict_item(dict_type, dict_code, dict_name, dict_desc) values (?, ?, ?, ?)",
                    "trade_status_oms", "NEW", "待确认", "交易系统A待确认状态");
            jdbc.update("insert into dict_item(dict_type, dict_code, dict_name, dict_desc) values (?, ?, ?, ?)",
                    "trade_status_oms", "DONE", "已成交", "交易系统A成交完成");
            jdbc.update("insert into dict_item(dict_type, dict_code, dict_name, dict_desc) values (?, ?, ?, ?)",
                    "trade_status_oms", "CANCEL", "已撤单", "交易系统A撤单状态");
            jdbc.update("insert into dict_item(dict_type, dict_code, dict_name, dict_desc) values (?, ?, ?, ?)",
                    "trade_status_broker", "A", "待确认", "交易系统B待确认状态");
            jdbc.update("insert into dict_item(dict_type, dict_code, dict_name, dict_desc) values (?, ?, ?, ?)",
                    "trade_status_broker", "S", "已成交", "交易系统B成交完成");
            jdbc.update("insert into dict_item(dict_type, dict_code, dict_name, dict_desc) values (?, ?, ?, ?)",
                    "trade_status_broker", "X", "已撤单", "交易系统B撤单状态");

            jdbc.update("insert into leaf_alloc(biz_tag, max_id, step, description) values (?, ?, ?, ?)",
                    "clean_trade", 100000L, 20, "中台标准交易主键");
            jdbc.update("insert into leaf_alloc(biz_tag, max_id, step, description) values (?, ?, ?, ?)",
                    "event_message", 500000L, 20, "同步事件主键");
            jdbc.update("insert into leaf_alloc(biz_tag, max_id, step, description) values (?, ?, ?, ?)",
                    "tx_audit", 900000L, 10, "事务审计主键");
        });
    }

    private void seedTradeSystemsFromMarketData(List<MarketstackService.StockSnapshot> snapshots) {
        log.info("[平台初始化] 开始写入交易系统A业务表，股票样本数={}", snapshots.size());
        jdbcExecutor.run(DS_TRADE_OMS, jdbc -> {
            for (int i = 0; i < snapshots.size(); i++) {
                MarketstackService.StockSnapshot snapshot = snapshots.get(i);
                insertOmsStockSnapshot(jdbc, snapshot, i + 1);
                for (int j = 0; j < OMS_ORDER_REPEAT; j++) {
                    insertOmsTradeSeed(jdbc, snapshot, i + 1, j + 1);
                }
                insertOmsPositionSeed(jdbc, snapshot, i + 1);
                if (i % 3 == 0) {
                    insertOmsCashSeed(jdbc, snapshot, i + 1);
                }
                logBootstrapProgress("交易系统A", i + 1, snapshots.size());
            }
        });
        log.info("[平台初始化] 交易系统A业务表写入完成");

        log.info("[平台初始化] 开始写入交易系统B业务表，股票样本数={}", snapshots.size());
        jdbcExecutor.run(DS_TRADE_BROKER, jdbc -> {
            for (int i = 0; i < snapshots.size(); i++) {
                MarketstackService.StockSnapshot snapshot = snapshots.get(i);
                insertBrokerStockQuote(jdbc, snapshot, i + 1);
                for (int j = 0; j < BROKER_DEAL_REPEAT; j++) {
                    insertBrokerTradeSeed(jdbc, snapshot, i + 1, j + 1);
                }
                insertBrokerPositionSeed(jdbc, snapshot, i + 1);
                if (i % 3 == 0) {
                    insertBrokerFundSeed(jdbc, snapshot, i + 1);
                }
                logBootstrapProgress("交易系统B", i + 1, snapshots.size());
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
        jdbcExecutor.run(DS_HUB, jdbc -> {
            jdbc.execute("drop table if exists tx_coordination_log");
            jdbc.execute("drop table if exists event_message");
            jdbc.execute("drop table if exists clean_trade");
            jdbc.execute("drop table if exists leaf_alloc");
            jdbc.execute("drop table if exists dict_item");
        });
    }

    private void dropOmsTradeTables() {
        jdbcExecutor.run(DS_TRADE_OMS, jdbc -> {
            jdbc.execute("drop table if exists oms_cash_asset");
            jdbc.execute("drop table if exists oms_position_holding");
            jdbc.execute("drop table if exists oms_trade_order");
            jdbc.execute("drop table if exists oms_stock_snapshot");
        });
    }

    private void dropBrokerTradeTables() {
        jdbcExecutor.run(DS_TRADE_BROKER, jdbc -> {
            jdbc.execute("drop table if exists broker_fund_account");
            jdbc.execute("drop table if exists broker_position_balance");
            jdbc.execute("drop table if exists broker_trade_deal");
            jdbc.execute("drop table if exists broker_stock_quote");
        });
    }

    private void createHubTables() {
        jdbcExecutor.run(DS_HUB, jdbc -> {
            jdbc.execute("create table dict_item (" +
                    "id bigint not null auto_increment primary key," +
                    "dict_type varchar(64) not null," +
                    "dict_code varchar(64) not null," +
                    "dict_name varchar(128) not null," +
                    "dict_desc varchar(256)," +
                    "unique key uk_dict_type_code(dict_type, dict_code))");
            jdbc.execute("create table leaf_alloc (" +
                    "biz_tag varchar(64) primary key," +
                    "max_id bigint not null," +
                    "step int not null," +
                    "description varchar(256))");
            jdbc.execute("create table clean_trade (" +
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
            jdbc.execute("create table event_message (" +
                    "message_id bigint primary key," +
                    "topic varchar(64) not null," +
                    "biz_key varchar(128) not null," +
                    "payload text not null," +
                    "status varchar(32) not null," +
                    "created_at varchar(32) not null)");
            jdbc.execute("create table tx_coordination_log (" +
                    "id bigint primary key," +
                    "source_system varchar(64) not null," +
                    "phase varchar(32) not null," +
                    "detail varchar(256) not null," +
                    "created_at varchar(32) not null)");
        });
    }

    private void createOmsTradeTables() {
        jdbcExecutor.run(DS_TRADE_OMS, jdbc -> {
            jdbc.execute("create table oms_stock_snapshot (" +
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
            jdbc.execute("create table oms_trade_order (" +
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
            jdbc.execute("create table oms_position_holding (" +
                    "id bigint not null auto_increment primary key," +
                    "investor_name varchar(64) not null," +
                    "stock_code varchar(16) not null," +
                    "holding_qty bigint not null," +
                    "available_qty bigint not null," +
                    "cost_price decimal(18,4) not null," +
                    "market_value decimal(18,2) not null," +
                    "stat_day varchar(16) not null)");
            jdbc.execute("create table oms_cash_asset (" +
                    "id bigint not null auto_increment primary key," +
                    "investor_name varchar(64) not null," +
                    "account_no varchar(64) not null," +
                    "cash_balance decimal(18,2) not null," +
                    "frozen_balance decimal(18,2) not null," +
                    "total_asset decimal(18,2) not null," +
                    "stat_day varchar(16) not null)");
        });
    }

    private void createBrokerTradeTables() {
        jdbcExecutor.run(DS_TRADE_BROKER, jdbc -> {
            jdbc.execute("create table broker_stock_quote (" +
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
            jdbc.execute("create table broker_trade_deal (" +
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
            jdbc.execute("create table broker_position_balance (" +
                    "id bigint not null auto_increment primary key," +
                    "client_full_name varchar(64) not null," +
                    "secu_code varchar(16) not null," +
                    "current_volume bigint not null," +
                    "enable_volume bigint not null," +
                    "cost_px decimal(18,4) not null," +
                    "market_amt decimal(18,2) not null," +
                    "biz_date varchar(16) not null)");
            jdbc.execute("create table broker_fund_account (" +
                    "id bigint not null auto_increment primary key," +
                    "client_full_name varchar(64) not null," +
                    "fund_account_no varchar(64) not null," +
                    "current_balance decimal(18,2) not null," +
                    "frozen_capital decimal(18,2) not null," +
                    "total_asset decimal(18,2) not null," +
                    "biz_date varchar(16) not null)");
        });
    }

    private void insertOmsStockSnapshot(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index) {
        jdbc.update("insert into oms_stock_snapshot(symbol, exchange_code, market_day, open_price, high_price, low_price, close_price, volume_qty, turnover_amount, sync_flag) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                snapshot.getSymbol(),
                defaultExchange(snapshot),
                normalizeTradeDay(snapshot.getDate()),
                resolvePrice(snapshot.getOpen()),
                resolvePrice(snapshot.getHigh()),
                resolvePrice(snapshot.getLow()),
                resolvePrice(snapshot.getClose()),
                resolveVolume(snapshot, index),
                resolveTurnover(snapshot, resolveVolume(snapshot, index)),
                0);
    }

    private void insertOmsTradeSeed(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index, int repeat) {
        long qty = resolveTradeQty(snapshot, index, repeat);
        BigDecimal price = resolveTradePrice(snapshot, repeat);
        jdbc.update("insert into oms_trade_order(order_no, stock_code, investor_name, side_code, trade_qty, trade_price, order_amount, trade_status, trade_time, sync_flag) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "OMS-" + snapshot.getSymbol() + "-" + String.format("%05d", index) + "-" + repeat,
                snapshot.getSymbol(),
                OMS_ACCOUNTS.get((index + repeat) % OMS_ACCOUNTS.size()),
                repeat % 2 == 0 ? "S" : "B",
                qty,
                price,
                price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP),
                OMS_STATUSES.get((index + repeat) % OMS_STATUSES.size()),
                normalizeTradeTime(snapshot.getDate()),
                0);
    }

    private void insertOmsPositionSeed(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index) {
        long holdingQty = resolvePositionQty(snapshot, index);
        BigDecimal price = resolveTradePrice(snapshot, 1);
        jdbc.update("insert into oms_position_holding(investor_name, stock_code, holding_qty, available_qty, cost_price, market_value, stat_day) values (?, ?, ?, ?, ?, ?, ?)",
                OMS_ACCOUNTS.get(index % OMS_ACCOUNTS.size()),
                snapshot.getSymbol(),
                holdingQty,
                Math.max(100L, holdingQty - 100L),
                price,
                price.multiply(BigDecimal.valueOf(holdingQty)).setScale(2, RoundingMode.HALF_UP),
                normalizeTradeDay(snapshot.getDate()));
    }

    private void insertOmsCashSeed(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index) {
        BigDecimal base = resolveTradePrice(snapshot, 2).multiply(BigDecimal.valueOf(10000L + index * 10L));
        jdbc.update("insert into oms_cash_asset(investor_name, account_no, cash_balance, frozen_balance, total_asset, stat_day) values (?, ?, ?, ?, ?, ?)",
                OMS_ACCOUNTS.get(index % OMS_ACCOUNTS.size()),
                "OMS-ACCT-" + String.format("%04d", index),
                base.setScale(2, RoundingMode.HALF_UP),
                base.multiply(BigDecimal.valueOf(0.08)).setScale(2, RoundingMode.HALF_UP),
                base.multiply(BigDecimal.valueOf(1.75)).setScale(2, RoundingMode.HALF_UP),
                normalizeTradeDay(snapshot.getDate()));
    }

    private void insertBrokerStockQuote(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index) {
        long volume = resolveVolume(snapshot, index + 17);
        jdbc.update("insert into broker_stock_quote(quote_code, secu_code, trade_day, exchange_name, open_px, high_px, low_px, close_px, vol_num, turnover_amt, sync_flag) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                snapshot.getSymbol() + "-" + normalizeTradeDay(snapshot.getDate()),
                snapshot.getSymbol(),
                normalizeTradeDay(snapshot.getDate()),
                defaultExchange(snapshot),
                resolvePrice(snapshot.getOpen()),
                resolvePrice(snapshot.getHigh()),
                resolvePrice(snapshot.getLow()),
                resolvePrice(snapshot.getClose()),
                volume,
                resolveTurnover(snapshot, volume),
                0);
    }

    private void insertBrokerTradeSeed(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index, int repeat) {
        long qty = resolveTradeQty(snapshot, index + 11, repeat + 1);
        BigDecimal price = resolveTradePrice(snapshot, repeat + 1);
        jdbc.update("insert into broker_trade_deal(deal_code, secu_code, client_full_name, bs_flag, deal_volume, deal_price, turnover_amount, status_mark, deal_at, sync_flag) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "BRK-" + snapshot.getSymbol() + "-" + String.format("%05d", index) + "-" + repeat,
                snapshot.getSymbol(),
                BROKER_CLIENTS.get((index + repeat) % BROKER_CLIENTS.size()),
                repeat % 2 == 0 ? "2" : "1",
                qty,
                price,
                price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP),
                BROKER_STATUSES.get((index + repeat) % BROKER_STATUSES.size()),
                normalizeTradeTime(snapshot.getDate()),
                0);
    }

    private void insertBrokerPositionSeed(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index) {
        long qty = resolvePositionQty(snapshot, index + 5);
        BigDecimal price = resolveTradePrice(snapshot, 2);
        jdbc.update("insert into broker_position_balance(client_full_name, secu_code, current_volume, enable_volume, cost_px, market_amt, biz_date) values (?, ?, ?, ?, ?, ?, ?)",
                BROKER_CLIENTS.get(index % BROKER_CLIENTS.size()),
                snapshot.getSymbol(),
                qty,
                Math.max(100L, qty - 200L),
                price,
                price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP),
                normalizeTradeDay(snapshot.getDate()));
    }

    private void insertBrokerFundSeed(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index) {
        BigDecimal base = resolveTradePrice(snapshot, 3).multiply(BigDecimal.valueOf(12000L + index * 12L));
        jdbc.update("insert into broker_fund_account(client_full_name, fund_account_no, current_balance, frozen_capital, total_asset, biz_date) values (?, ?, ?, ?, ?, ?)",
                BROKER_CLIENTS.get(index % BROKER_CLIENTS.size()),
                "FUND-" + String.format("%04d", index),
                base.setScale(2, RoundingMode.HALF_UP),
                base.multiply(BigDecimal.valueOf(0.06)).setScale(2, RoundingMode.HALF_UP),
                base.multiply(BigDecimal.valueOf(1.68)).setScale(2, RoundingMode.HALF_UP),
                normalizeTradeDay(snapshot.getDate()));
    }

    private long resolveTradeQty(MarketstackService.StockSnapshot snapshot, int index, int repeat) {
        long base = resolveVolume(snapshot, index) % 5000L;
        return ((base / 100L) + 5L + repeat) * 100L;
    }

    private long resolvePositionQty(MarketstackService.StockSnapshot snapshot, int index) {
        long base = resolveVolume(snapshot, index) % 8000L;
        return ((base / 100L) + 10L) * 100L;
    }

    private long resolveVolume(MarketstackService.StockSnapshot snapshot, int index) {
        if (snapshot.getVolume() == null) {
            return 50000L + index * 500L;
        }
        long volume = snapshot.getVolume().longValue();
        return Math.max(1000L, volume);
    }

    private BigDecimal resolveTradePrice(MarketstackService.StockSnapshot snapshot, int bias) {
        BigDecimal basePrice = resolveBasePrice(snapshot);
        return basePrice.add(BigDecimal.valueOf(bias).multiply(BigDecimal.valueOf(0.03))).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveBasePrice(MarketstackService.StockSnapshot snapshot) {
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

    private BigDecimal resolveTurnover(MarketstackService.StockSnapshot snapshot, long volume) {
        return resolveBasePrice(snapshot).multiply(BigDecimal.valueOf(volume)).setScale(2, RoundingMode.HALF_UP);
    }

    private String defaultExchange(MarketstackService.StockSnapshot snapshot) {
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
        return jdbcExecutor.query(dataSourceKey, jdbc -> {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (String table : tables) {
                Integer count = jdbc.queryForObject("select count(1) from " + table, Integer.class);
                result.put(table, count == null ? 0 : count);
            }
            return result;
        });
    }

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
