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
        initializeSchema();
        resetDemoData();
        log.info("[平台初始化] schema、表结构和初始股票数据准备完成");
    }

    public synchronized void resetDemoData() {
        log.info("[平台初始化] 开始重置演示数据");
        initializeSchema();
        initHubBaseData();
        List<MarketstackService.StockSnapshot> snapshots = marketstackService.fetchLatestStocks();
        seedTradeSystemsFromMarketData(snapshots);
        leafSegmentService.clearLocalCache();
        log.info("[平台初始化] 演示数据重置完成，上游股票条数={}", snapshots.size());
    }

    public Map<String, Object> currentTopology() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hub", DS_HUB);
        map.put("upstreams", List.of(
                Map.of("key", DS_TRADE_OMS, "type", TYPE_TRADE_OMS, "table", "oms_trade_order"),
                Map.of("key", DS_TRADE_BROKER, "type", TYPE_TRADE_BROKER, "table", "broker_trade_deal")
        ));
        return map;
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

    private void initializeSchema() {
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
            jdbc.execute("delete from dict_item");
            jdbc.execute("delete from leaf_alloc");
            jdbc.execute("delete from clean_trade");
            jdbc.execute("delete from event_message");
            jdbc.execute("delete from tx_coordination_log");

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
        jdbcExecutor.run(DS_TRADE_OMS, jdbc -> {
            jdbc.execute("delete from oms_trade_order");
            for (int i = 0; i < snapshots.size(); i++) {
                insertOmsTradeSeed(jdbc, snapshots.get(i), i + 1);
            }
        });

        jdbcExecutor.run(DS_TRADE_BROKER, jdbc -> {
            jdbc.execute("delete from broker_trade_deal");
            for (int i = 0; i < snapshots.size(); i++) {
                insertBrokerTradeSeed(jdbc, snapshots.get(i), i + 1);
            }
        });
    }

    private void createHubTables() {
        jdbcExecutor.run(DS_HUB, jdbc -> {
            jdbc.execute("create table if not exists dict_item (" +
                    "id bigint not null auto_increment primary key," +
                    "dict_type varchar(64) not null," +
                    "dict_code varchar(64) not null," +
                    "dict_name varchar(128) not null," +
                    "dict_desc varchar(256)," +
                    "unique key uk_dict_type_code(dict_type, dict_code))");
            jdbc.execute("create table if not exists leaf_alloc (" +
                    "biz_tag varchar(64) primary key," +
                    "max_id bigint not null," +
                    "step int not null," +
                    "description varchar(256))");
            jdbc.execute("create table if not exists clean_trade (" +
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
            jdbc.execute("create table if not exists event_message (" +
                    "message_id bigint primary key," +
                    "topic varchar(64) not null," +
                    "biz_key varchar(128) not null," +
                    "payload text not null," +
                    "status varchar(32) not null," +
                    "created_at varchar(32) not null)");
            jdbc.execute("create table if not exists tx_coordination_log (" +
                    "id bigint primary key," +
                    "source_system varchar(64) not null," +
                    "phase varchar(32) not null," +
                    "detail varchar(256) not null," +
                    "created_at varchar(32) not null)");
        });
    }

    private void createOmsTradeTables() {
        jdbcExecutor.run(DS_TRADE_OMS, jdbc -> jdbc.execute(
                "create table if not exists oms_trade_order (" +
                        "id bigint not null auto_increment primary key," +
                        "order_no varchar(64) not null," +
                        "investor_name varchar(64) not null," +
                        "side_code varchar(8) not null," +
                        "order_amount decimal(18,2) not null," +
                        "trade_status varchar(32) not null," +
                        "trade_time varchar(32) not null," +
                        "sync_flag int default 0 not null)"));
    }

    private void createBrokerTradeTables() {
        jdbcExecutor.run(DS_TRADE_BROKER, jdbc -> jdbc.execute(
                "create table if not exists broker_trade_deal (" +
                        "id bigint not null auto_increment primary key," +
                        "deal_code varchar(64) not null," +
                        "client_full_name varchar(64) not null," +
                        "bs_flag varchar(8) not null," +
                        "turnover_amount decimal(18,2) not null," +
                        "status_mark varchar(32) not null," +
                        "deal_at varchar(32) not null," +
                        "sync_flag int default 0 not null)"));
    }

    private void insertOmsTradeSeed(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index) {
        jdbc.update("insert into oms_trade_order(order_no, investor_name, side_code, order_amount, trade_status, trade_time, sync_flag) values (?, ?, ?, ?, ?, ?, ?)",
                "OMS-" + snapshot.getSymbol() + "-" + String.format("%03d", index),
                "策略账户-" + snapshot.getSymbol(),
                index % 2 == 0 ? "S" : "B",
                resolveAmount(snapshot),
                OMS_STATUSES.get(index % OMS_STATUSES.size()),
                normalizeTradeTime(snapshot.getDate()),
                0);
    }

    private void insertBrokerTradeSeed(JdbcTemplate jdbc, MarketstackService.StockSnapshot snapshot, int index) {
        jdbc.update("insert into broker_trade_deal(deal_code, client_full_name, bs_flag, turnover_amount, status_mark, deal_at, sync_flag) values (?, ?, ?, ?, ?, ?, ?)",
                "BRK-" + snapshot.getSymbol() + "-" + String.format("%03d", index),
                "券商通道-" + snapshot.getSymbol(),
                index % 2 == 0 ? "2" : "1",
                resolveAmount(snapshot),
                BROKER_STATUSES.get(index % BROKER_STATUSES.size()),
                normalizeTradeTime(snapshot.getDate()),
                0);
    }

    private BigDecimal resolveAmount(MarketstackService.StockSnapshot snapshot) {
        BigDecimal price = snapshot.getClose() != null ? snapshot.getClose() : snapshot.getOpen();
        if (price == null) {
            return BigDecimal.valueOf(10000);
        }
        return price.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeTradeTime(String marketstackDate) {
        if (marketstackDate == null || marketstackDate.isBlank()) {
            return LocalDateTime.now().format(FORMATTER);
        }
        String normalized = marketstackDate.replace('T', ' ');
        return normalized.length() >= 19 ? normalized.substring(0, 19) : normalized;
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
