package com.example.dynamicds.service;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingJdbcExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PlatformBootstrapService {

    public static final String DS_META = "meta_center";
    public static final String DS_WAREHOUSE = "risk_warehouse";
    public static final String DS_EQUITY = "trade_equity";
    public static final String DS_FUTURES = "trade_futures";
    public static final String DS_BOND = "trade_bond";

    private final DynamicDataSourceManager manager;
    private final RoutingJdbcExecutor jdbcExecutor;
    private final LeafSegmentService leafSegmentService;

    public PlatformBootstrapService(DynamicDataSourceManager manager,
                                    RoutingJdbcExecutor jdbcExecutor,
                                    LeafSegmentService leafSegmentService) {
        this.manager = manager;
        this.jdbcExecutor = jdbcExecutor;
        this.leafSegmentService = leafSegmentService;
    }

    @PostConstruct
    public void init() {
        ensureDataSource(DS_META, "元数据中心");
        ensureDataSource(DS_WAREHOUSE, "风控底座");
        ensureDataSource(DS_EQUITY, "股票交易");
        ensureDataSource(DS_FUTURES, "期货交易");
        ensureDataSource(DS_BOND, "债券交易");
        initializeSchema();
        resetDemoData();
    }

    public synchronized void resetDemoData() {
        createMetaTables();
        createWarehouseTables();
        createTradeTables(DS_EQUITY);
        createTradeTables(DS_FUTURES);
        createTradeTables(DS_BOND);

        jdbcExecutor.run(DS_META, jdbc -> {
            jdbc.execute("delete from dict_item");
            jdbc.execute("delete from leaf_alloc");
            jdbc.update("insert into dict_item(dict_type, dict_code, dict_name, dict_desc) values " +
                    "('trade_status','0','待报','交易所回执前状态')," +
                    "('trade_status','1','已报','订单已送交易所')," +
                    "('trade_status','2','已成','成交完成')," +
                    "('trade_status','9','废单','异常或撤单')," +
                    "('counterparty','C001','中信证券','券商侧简称')," +
                    "('counterparty','C002','国泰君安','券商侧简称')," +
                    "('counterparty','C003','招商证券','券商侧简称')");
            jdbc.update("insert into leaf_alloc(biz_tag, max_id, step, description) values " +
                    "('clean_trade', 100000, 20, '清洗后交易主键')," +
                    "('event_message', 500000, 50, '标准事件消息')," +
                    "('tx_audit', 900000, 10, '跨库事务审计')");
        });

        jdbcExecutor.run(DS_WAREHOUSE, jdbc -> {
            jdbc.execute("delete from clean_trade");
            jdbc.execute("delete from event_message");
            jdbc.execute("delete from tx_coordination_log");
        });

        seedTradeRows(DS_EQUITY, "EQT", "股票");
        seedTradeRows(DS_FUTURES, "FUT", "期货");
        seedTradeRows(DS_BOND, "BND", "债券");
        leafSegmentService.clearLocalCache();
    }

    public Map<String, Object> currentTopology() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("metaCenter", DS_META);
        map.put("warehouse", DS_WAREHOUSE);
        map.put("tradeSystems", new String[]{DS_EQUITY, DS_FUTURES, DS_BOND});
        return map;
    }

    private void initializeSchema() {
        createMetaTables();
        createWarehouseTables();
        createTradeTables(DS_EQUITY);
        createTradeTables(DS_FUTURES);
        createTradeTables(DS_BOND);
    }

    private void ensureDataSource(String key, String poolAlias) {
        if (manager.exists(key)) {
            return;
        }
        DataSourceConfigDTO config = new DataSourceConfigDTO();
        config.setKey(key);
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");
        config.setPoolName("HikariPool-" + poolAlias);
        config.setMaxPoolSize(8);
        config.setMinIdle(1);
        config.setUrl("jdbc:h2:mem:" + key + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        manager.register(config);
    }

    private void createMetaTables() {
        jdbcExecutor.run(DS_META, jdbc -> {
            jdbc.execute("create table if not exists dict_item (" +
                    "id bigint auto_increment primary key," +
                    "dict_type varchar(64) not null," +
                    "dict_code varchar(64) not null," +
                    "dict_name varchar(128) not null," +
                    "dict_desc varchar(256)," +
                    "unique(dict_type, dict_code))");
            jdbc.execute("create table if not exists leaf_alloc (" +
                    "biz_tag varchar(64) primary key," +
                    "max_id bigint not null," +
                    "step int not null," +
                    "description varchar(256))");
        });
    }

    private void createWarehouseTables() {
        jdbcExecutor.run(DS_WAREHOUSE, jdbc -> {
            jdbc.execute("create table if not exists clean_trade (" +
                    "global_id bigint primary key," +
                    "source_system varchar(64) not null," +
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
                    "payload clob not null," +
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

    private void createTradeTables(String dataSourceKey) {
        jdbcExecutor.run(dataSourceKey, jdbc -> jdbc.execute(
                "create table if not exists raw_trade (" +
                        "id bigint auto_increment primary key," +
                        "vendor_trade_no varchar(64) not null," +
                        "biz_type varchar(64) not null," +
                        "direction_code varchar(8) not null," +
                        "amount decimal(18,2) not null," +
                        "status_code varchar(8) not null," +
                        "counterparty_code varchar(16) not null," +
                        "trade_time varchar(32) not null," +
                        "cleaned int default 0 not null)"));
    }

    private void seedTradeRows(String ds, String prefix, String bizType) {
        jdbcExecutor.run(ds, jdbc -> {
            jdbc.execute("delete from raw_trade");
            for (int i = 1; i <= 5; i++) {
                jdbc.update("insert into raw_trade(vendor_trade_no, biz_type, direction_code, amount, status_code, counterparty_code, trade_time, cleaned) " +
                                "values (?,?,?,?,?,?,?,0)",
                        prefix + "-" + i,
                        bizType,
                        i % 2 == 0 ? "S" : "B",
                        BigDecimal.valueOf(1000 + i * 137L),
                        i % 4 == 0 ? "2" : String.valueOf(i % 3),
                        "C00" + ((i % 3) + 1),
                        LocalDateTime.now().minusMinutes(i).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
        });
    }
}
