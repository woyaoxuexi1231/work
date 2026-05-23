package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.bootstrap.BrokerDemoDataSeeder;
import com.example.dynamicds.bootstrap.MarketSeedSnapshot;
import com.example.dynamicds.bootstrap.OmsDemoDataSeeder;
import com.example.dynamicds.bootstrap.SqlScriptExecutor;
import com.example.dynamicds.config.HubDataSourceProperties;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.entity.DictItem;
import com.example.dynamicds.entity.LeafAlloc;
import com.example.dynamicds.mapper.DictItemMapper;
import com.example.dynamicds.mapper.DynamicSqlMapper;
import com.example.dynamicds.mapper.LeafAllocMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformBootstrapService {

    private static final int BOOTSTRAP_PROGRESS_STEP = 200;
    private static final String HUB_SCHEMA_SCRIPT = "sql/bootstrap/hub-schema.sql";
    private static final String OMS_SCHEMA_SCRIPT = "sql/bootstrap/oms-schema.sql";
    private static final String BROKER_SCHEMA_SCRIPT = "sql/bootstrap/broker-schema.sql";
    private static final String CLEAR_OMS_SCRIPT = "sql/bootstrap/clear-oms.sql";
    private static final String CLEAR_BROKER_SCRIPT = "sql/bootstrap/clear-broker.sql";
    private static final String CLEAR_HUB_SCRIPT = "sql/bootstrap/clear-hub.sql";

    private static final String TAG_DICT_ITEM = "dict_item";
    private static final String TAG_INIT_TASK = "init_task";
    private static final String TAG_SYNC_TASK = "sync_task";
    private static final String TAG_SYNC_BUSINESS_RECORD = "sync_business_record";
    private static final String TAG_TX_COORDINATION_LOG = "tx_coordination_log";

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
    private final SqlScriptExecutor sqlScriptExecutor;
    private final OmsDemoDataSeeder omsDemoDataSeeder;
    private final BrokerDemoDataSeeder brokerDemoDataSeeder;
    private final DictItemMapper dictItemMapper;
    private final LeafAllocMapper leafAllocMapper;

    @Value("${spring.datasource.url}")
    private String hubUrl;

    @Value("${spring.datasource.username}")
    private String hubUser;

    @Value("${spring.datasource.password}")
    private String hubPwd;

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
            initHubBaseData();
        }
        leafSegmentService.clearLocalCache();
        log.info("[平台初始化] 完成");
    }

    private void ensureTradeSchemas() {
        String prefix = hubUrl.substring(0, hubUrl.indexOf('/', "jdbc:mysql://".length()));
        String query = hubUrl.contains("?") ? hubUrl.substring(hubUrl.indexOf('?')) : "";
        String baseUrl = prefix + query;
        for (String db : List.of(DS_TRADE_OMS, DS_TRADE_BROKER)) {
            try (Connection connection = DriverManager.getConnection(baseUrl, hubUser, hubPwd);
                 Statement statement = connection.createStatement()) {
                statement.execute("create database if not exists `" + db + "` default character set utf8mb4");
                log.info("[平台初始化] schema={}", db);
            } catch (Exception e) {
                throw new IllegalStateException("create schema failed: " + db, e);
            }
        }
    }

    private void ensureLatestSchema() {
        runScript(DS_HUB, HUB_SCHEMA_SCRIPT);
        runScript(DS_TRADE_OMS, OMS_SCHEMA_SCRIPT);
        runScript(DS_TRADE_BROKER, BROKER_SCHEMA_SCRIPT);
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

    public synchronized Map<String, Object> initDemoData() {
        log.info("[平台初始化] 开始初始化演示数据");
        clearBusinessDataTables();
        leafSegmentService.clearLocalCache();
        initHubBaseData();
        List<MarketSeedSnapshot> snapshots = MarketSeedSnapshot.fallbackSeeds();
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
        progressCallback.accept(5);
        leafSegmentService.clearLocalCache();
        initHubBaseData();
        progressCallback.accept(10);

        List<MarketSeedSnapshot> snapshots = MarketSeedSnapshot.fallbackSeeds();
        progressCallback.accept(15);

        seedOmsSystemsWithProgress(snapshots, progressCallback, 15, 60);
        seedBrokerSystemsWithProgress(snapshots, progressCallback, 60, 95);

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

    private void clearBusinessDataTables() {
        runScript(DS_TRADE_OMS, CLEAR_OMS_SCRIPT);
        runScript(DS_TRADE_BROKER, CLEAR_BROKER_SCRIPT);
        runScript(DS_HUB, CLEAR_HUB_SCRIPT);
    }

    private void initHubBaseData() {
        routingMybatisExecutor.run(DS_HUB, () -> {
            for (LeafAllocSpec spec : leafAllocSpecs()) {
                ensureLeafAlloc(spec.bizTag(), spec.dataSourceKey(), spec.tableName(),
                        spec.columnName(), spec.baseMaxId(), spec.step(), spec.description());
            }
            for (DictSeedSpec spec : dictSeedSpecs()) {
                upsertDictItem(spec.dictType(), spec.dictCode(), spec.dictName(), spec.dictDesc());
            }
        });
    }

    private void seedTradeSystemsFromMarketData(List<MarketSeedSnapshot> snapshots) {
        log.info("[平台初始化] 写入交易系统A, size={}", snapshots.size());
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            for (int i = 0; i < snapshots.size(); i++) {
                omsDemoDataSeeder.seed(snapshots.get(i), i);
                logBootstrapProgress("交易系统A", i + 1, snapshots.size());
            }
        });

        log.info("[平台初始化] 写入交易系统B, size={}", snapshots.size());
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            for (int i = 0; i < snapshots.size(); i++) {
                brokerDemoDataSeeder.seed(snapshots.get(i), i);
                logBootstrapProgress("交易系统B", i + 1, snapshots.size());
            }
        });
    }

    private void seedOmsSystemsWithProgress(List<MarketSeedSnapshot> snapshots,
                                            IntConsumer progressCallback, int progressStart, int progressEnd) {
        int total = snapshots.size();
        routingMybatisExecutor.run(DS_TRADE_OMS, () -> {
            for (int i = 0; i < total; i++) {
                omsDemoDataSeeder.seed(snapshots.get(i), i);
                progressCallback.accept(progressStart + (int) ((i + 1) / (double) total * (progressEnd - progressStart)));
            }
        });
    }

    private void seedBrokerSystemsWithProgress(List<MarketSeedSnapshot> snapshots,
                                               IntConsumer progressCallback, int progressStart, int progressEnd) {
        int total = snapshots.size();
        routingMybatisExecutor.run(DS_TRADE_BROKER, () -> {
            for (int i = 0; i < total; i++) {
                brokerDemoDataSeeder.seed(snapshots.get(i), i);
                progressCallback.accept(progressStart + (int) ((i + 1) / (double) total * (progressEnd - progressStart)));
            }
        });
    }

    private void logBootstrapProgress(String systemName, int current, int total) {
        if (current == total || current % BOOTSTRAP_PROGRESS_STEP == 0) {
            log.info("[平台初始化] {} {}/{}", systemName, current, total);
        }
    }

    public Map<String, List<String>> currentTopology() {
        Map<String, List<String>> topology = new LinkedHashMap<>();
        List<String> sources = manager.keys().stream()
                .filter(key -> !DS_HUB.equals(key))
                .map(key -> {
                    DataSourceConfigDTO config = manager.getConfig(key);
                    return key + " (" + (config != null ? config.getName() : "?") + ")";
                })
                .toList();
        topology.put("上游业务系统", sources);
        topology.put("中台库", List.of(DS_HUB));
        return topology;
    }

    public Map<String, Object> currentBusinessTableStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put(DS_TRADE_OMS, countTables(DS_TRADE_OMS,
                List.of("oms_stock_snapshot", "oms_trade_order", "oms_position_holding", "oms_cash_asset")));
        stats.put(DS_TRADE_BROKER, countTables(DS_TRADE_BROKER,
                List.of("broker_stock_quote", "broker_trade_deal", "broker_position_balance", "broker_fund_account")));
        return stats;
    }

    public Map<String, Integer> currentHubTableStats() {
        return countTables(DS_HUB,
                List.of("clean_stock", "clean_trade", "clean_position", "clean_asset",
                        "event_message", "init_task", "sync_task", "sync_business_record"));
    }

    private void runScript(String dataSourceKey, String classpathLocation) {
        routingMybatisExecutor.run(dataSourceKey, () -> sqlScriptExecutor.executeClasspathScript(classpathLocation));
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

    private void ensureLeafAlloc(String bizTag, String dataSourceKey, String tableName, String columnName,
                                 long baseMaxId, int step, String description) {
        long currentMaxId = maxValue(dataSourceKey, tableName, columnName);
        long seedMaxId = Math.max(baseMaxId, currentMaxId);
        LeafAlloc existing = leafAllocMapper.selectById(bizTag);
        if (existing == null) {
            leafAllocMapper.insert(buildLeafAlloc(bizTag, seedMaxId, step, description));
            return;
        }
        boolean changed = false;
        if (existing.getMaxId() == null || existing.getMaxId() < seedMaxId) {
            existing.setMaxId(seedMaxId);
            changed = true;
        }
        if (existing.getStep() == null || existing.getStep() != step) {
            existing.setStep(step);
            changed = true;
        }
        if (!Objects.equals(existing.getDescription(), description)) {
            existing.setDescription(description);
            changed = true;
        }
        if (changed) {
            leafAllocMapper.updateById(existing);
        }
    }

    private void upsertDictItem(String dictType, String dictCode, String dictName, String dictDesc) {
        DictItem existing = dictItemMapper.selectOne(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictType, dictType)
                .eq(DictItem::getDictCode, dictCode)
                .last("limit 1"));
        if (existing == null) {
            dictItemMapper.insert(buildDictItem(dictType, dictCode, dictName, dictDesc));
            return;
        }
        existing.setDictName(dictName);
        existing.setDictDesc(dictDesc);
        dictItemMapper.updateById(existing);
    }

    private Map<String, Integer> countTables(String dataSourceKey, List<String> tables) {
        return routingMybatisExecutor.query(dataSourceKey, () -> {
            Map<String, Integer> stats = new LinkedHashMap<>();
            for (String table : tables) {
                stats.put(table, dynamicSqlMapper.countTable(table));
            }
            return stats;
        });
    }

    private long maxValue(String dataSourceKey, String tableName, String columnName) {
        Long value = routingMybatisExecutor.query(dataSourceKey,
                () -> dynamicSqlMapper.maxValue(tableName, columnName));
        return value == null ? 0L : value;
    }

    private List<LeafAllocSpec> leafAllocSpecs() {
        return List.of(
                new LeafAllocSpec(TAG_INIT_TASK, DS_HUB, "init_task", "id", 0L, 10, "初始化任务主键"),
                new LeafAllocSpec(TAG_SYNC_TASK, DS_HUB, "sync_task", "id", 0L, 10, "同步任务主键"),
                new LeafAllocSpec(TAG_SYNC_BUSINESS_RECORD, DS_HUB, "sync_business_record", "id", 0L, 20, "同步业务记录主键"),
                new LeafAllocSpec(TAG_DICT_ITEM, DS_HUB, "dict_item", "id", 0L, 20, "字典项主键"),
                new LeafAllocSpec("oms_stock_snapshot", DS_TRADE_OMS, "oms_stock_snapshot", "id", 0L, 20, "OMS股票快照主键"),
                new LeafAllocSpec("oms_trade_order", DS_TRADE_OMS, "oms_trade_order", "id", 0L, 20, "OMS交易订单主键"),
                new LeafAllocSpec("oms_position_holding", DS_TRADE_OMS, "oms_position_holding", "id", 0L, 20, "OMS持仓主键"),
                new LeafAllocSpec("oms_cash_asset", DS_TRADE_OMS, "oms_cash_asset", "id", 0L, 20, "OMS资金主键"),
                new LeafAllocSpec("broker_stock_quote", DS_TRADE_BROKER, "broker_stock_quote", "id", 0L, 20, "券商行情主键"),
                new LeafAllocSpec("broker_trade_deal", DS_TRADE_BROKER, "broker_trade_deal", "id", 0L, 20, "券商成交主键"),
                new LeafAllocSpec("broker_position_balance", DS_TRADE_BROKER, "broker_position_balance", "id", 0L, 20, "券商持仓主键"),
                new LeafAllocSpec("broker_fund_account", DS_TRADE_BROKER, "broker_fund_account", "id", 0L, 20, "券商资金主键"),
                new LeafAllocSpec("clean_stock", DS_HUB, "clean_stock", "global_id", 49_999L, 20, "中台标准股票主键"),
                new LeafAllocSpec("clean_trade", DS_HUB, "clean_trade", "global_id", 99_999L, 20, "中台标准交易主键"),
                new LeafAllocSpec("clean_position", DS_HUB, "clean_position", "global_id", 199_999L, 20, "中台标准持仓主键"),
                new LeafAllocSpec("clean_asset", DS_HUB, "clean_asset", "global_id", 299_999L, 20, "中台标准资金主键"),
                new LeafAllocSpec("event_message", DS_HUB, "event_message", "message_id", 499_999L, 20, "同步事件主键"),
                new LeafAllocSpec(TAG_TX_COORDINATION_LOG, DS_HUB, "tx_coordination_log", "id", 899_999L, 10, "事务审计主键")
        );
    }

    private List<DictSeedSpec> dictSeedSpecs() {
        return List.of(
                new DictSeedSpec("trade_status_oms", "NEW", "待确认", "交易系统A待确认状态"),
                new DictSeedSpec("trade_status_oms", "DONE", "已成交", "交易系统A成交完成"),
                new DictSeedSpec("trade_status_oms", "CANCEL", "已撤单", "交易系统A撤单状态"),
                new DictSeedSpec("trade_status_broker", "A", "待确认", "交易系统B待确认状态"),
                new DictSeedSpec("trade_status_broker", "S", "已成交", "交易系统B成交完成"),
                new DictSeedSpec("trade_status_broker", "X", "已撤单", "交易系统B撤单状态")
        );
    }

    private record LeafAllocSpec(String bizTag, String dataSourceKey, String tableName,
                                 String columnName, long baseMaxId, int step, String description) {
    }

    private record DictSeedSpec(String dictType, String dictCode, String dictName, String dictDesc) {
    }
}
