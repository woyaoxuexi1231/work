package com.example.dynamicds.bootstrap;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.DictItem;
import com.example.dynamicds.service.LeafSegmentService;
import com.example.dynamicds.entity.LeafAlloc;
import com.example.dynamicds.mapper.DictItemMapper;
import com.example.dynamicds.mapper.DynamicSqlMapper;
import com.example.dynamicds.mapper.LeafAllocMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 中台库初始化 — 建表 + 基础数据（字典、发号器）。
 * 由 {@code app.ddl.enabled} 控制是否执行 DDL。
 */
@Slf4j
@Service
public class HubInitializer {

    private static final String TAG_DICT_ITEM = "dict_item";
    private static final String TAG_INIT_TASK = "init_task";
    private static final String TAG_SYNC_TASK = "sync_task";
    private static final String TAG_OMS_SNAPSHOT = "oms_stock_snapshot";
    private static final String TAG_OMS_ORDER = "oms_trade_order";
    private static final String TAG_OMS_POSITION = "oms_position_holding";
    private static final String TAG_OMS_CASH = "oms_cash_asset";
    private static final String TAG_BROKER_QUOTE = "broker_stock_quote";
    private static final String TAG_BROKER_DEAL = "broker_trade_deal";
    private static final String TAG_BROKER_POSITION = "broker_position_balance";
    private static final String TAG_BROKER_FUND = "broker_fund_account";

    private final DynamicDataSourceManager manager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final LeafSegmentService leafSegmentService;
    private final DynamicSqlMapper dynamicSqlMapper;
    private final DictItemMapper dictItemMapper;
    private final LeafAllocMapper leafAllocMapper;
    private final String hubUrl;

    @Value("${app.ddl.enabled:true}")
    private boolean ddlEnabled;

    public HubInitializer(DynamicDataSourceManager manager,
                          RoutingMybatisExecutor routingMybatisExecutor,
                          LeafSegmentService leafSegmentService,
                          DynamicSqlMapper dynamicSqlMapper,
                          DictItemMapper dictItemMapper,
                          LeafAllocMapper leafAllocMapper,
                          @Value("${spring.datasource.url}") String hubUrl) {
        this.manager = manager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.leafSegmentService = leafSegmentService;
        this.dynamicSqlMapper = dynamicSqlMapper;
        this.dictItemMapper = dictItemMapper;
        this.leafAllocMapper = leafAllocMapper;
        this.hubUrl = hubUrl;
    }

    @PostConstruct
    public void init() {
        manager.putHubConfig(HubConstants.DS_HUB, "中台库", hubUrl);
        if (ddlEnabled) {
            createHubTables();
        }
        initHubBaseData();
        leafSegmentService.clearLocalCache();
        log.info("[HubInitializer] 中台库初始化完成, ddl.enabled={}", ddlEnabled);
    }

    // ============ DDL ============

    private void createHubTables() {
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            executeSql("create table if not exists dict_item (id bigint primary key,dict_type varchar(64) not null,dict_code varchar(64) not null,dict_name varchar(128) not null,dict_desc varchar(256),unique key uk_dict_type_code(dict_type,dict_code))");
            executeSql("create table if not exists leaf_alloc (biz_tag varchar(64) primary key,max_id bigint not null,step int not null,description varchar(256))");
            executeSql("create table if not exists clean_stock (global_id bigint primary key,source_system varchar(64) not null,source_type varchar(32) not null,source_row_id bigint not null,stock_code varchar(32) not null,exchange_code varchar(32),market_day varchar(16) not null,open_price decimal(18,4) not null,high_price decimal(18,4) not null,low_price decimal(18,4) not null,close_price decimal(18,4) not null,volume_qty bigint not null,turnover_amount decimal(18,2) not null,clean_batch varchar(64) not null,created_at varchar(32) not null)");
            executeSql("create table if not exists clean_trade (global_id bigint primary key,source_system varchar(64) not null,source_type varchar(32) not null,source_row_id bigint not null,vendor_trade_no varchar(64) not null,biz_type varchar(64) not null,direction varchar(32) not null,amount decimal(18,2) not null,status_name varchar(64) not null,counterparty_name varchar(128),clean_mode varchar(32) not null,clean_batch varchar(64) not null,trade_time varchar(32) not null,created_at varchar(32) not null)");
            executeSql("create table if not exists clean_position (global_id bigint primary key,source_system varchar(64) not null,source_type varchar(32) not null,source_row_id bigint not null,account_name varchar(128) not null,stock_code varchar(32) not null,holding_qty bigint not null,available_qty bigint not null,cost_price decimal(18,4) not null,market_value decimal(18,2) not null,stat_day varchar(16) not null,clean_batch varchar(64) not null,created_at varchar(32) not null)");
            executeSql("create table if not exists clean_asset (global_id bigint primary key,source_system varchar(64) not null,source_type varchar(32) not null,source_row_id bigint not null,account_name varchar(128) not null,account_no varchar(64) not null,cash_balance decimal(18,2) not null,frozen_balance decimal(18,2) not null,total_asset decimal(18,2) not null,stat_day varchar(16) not null,clean_batch varchar(64) not null,created_at varchar(32) not null)");
            executeSql("create table if not exists event_message (message_id bigint primary key,topic varchar(64) not null,biz_key varchar(128) not null,payload text not null,status varchar(32) not null,created_at varchar(32) not null)");
            executeSql("create table if not exists tx_coordination_log (id bigint primary key,source_system varchar(64) not null,phase varchar(32) not null,detail varchar(256) not null,created_at varchar(32) not null)");
            executeSql("create table if not exists init_task (id bigint primary key,status varchar(32) not null default 'IDLE',submitted_at varchar(32),started_at varchar(32),finished_at varchar(32),progress int default 0,message varchar(256),error_message varchar(1024),result text)");
            executeSql("create table if not exists sync_task (id bigint primary key,data_source_key varchar(64),data_source_name varchar(128),datasource_type varchar(32),page_size int default 2,status varchar(32) not null default 'IDLE',progress int default 0,total_pulled_count int default 0,total_saved_count int default 0,submitted_at varchar(32),started_at varchar(32),finished_at varchar(32),message varchar(256),error_message varchar(1024))");
            executeSql("create table if not exists sync_business_record (id bigint primary key,task_id bigint not null,business_code varchar(32) not null,status varchar(32) not null default 'RUNNING',page_count int default 0,pulled_count int default 0,saved_count int default 0,last_row_id bigint default 0,error_message varchar(1024),started_at varchar(32),finished_at varchar(32),key idx_record_task_id(task_id))");
            safeAddColumn("init_task", "progress int default 0");
            safeAddColumn("sync_task", "progress int default 0");
        });
    }

    // ============ 基础数据 ============

    private void initHubBaseData() {
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            dictItemMapper.insert(buildDictItem("trade_status_oms", "NEW", "待确认", "OMS待确认"));
            dictItemMapper.insert(buildDictItem("trade_status_oms", "DONE", "已成交", "OMS成交完成"));
            dictItemMapper.insert(buildDictItem("trade_status_oms", "CANCEL", "已撤单", "OMS撤单状态"));
            dictItemMapper.insert(buildDictItem("trade_status_broker", "A", "待确认", "Broker待确认"));
            dictItemMapper.insert(buildDictItem("trade_status_broker", "S", "已成交", "Broker成交完成"));
            dictItemMapper.insert(buildDictItem("trade_status_broker", "X", "已撤单", "Broker撤单状态"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_INIT_TASK, 1L, 10, "init_task主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_SYNC_TASK, 1L, 10, "sync_task主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_DICT_ITEM, 1L, 20, "dict_item主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_OMS_SNAPSHOT, 1L, 20, "oms_stock_snapshot主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_OMS_ORDER, 1L, 20, "oms_trade_order主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_OMS_POSITION, 1L, 20, "oms_position_holding主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_OMS_CASH, 1L, 20, "oms_cash_asset主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_BROKER_QUOTE, 1L, 20, "broker_stock_quote主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_BROKER_DEAL, 1L, 20, "broker_trade_deal主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_BROKER_POSITION, 1L, 20, "broker_position_balance主键"));
            leafAllocMapper.insert(buildLeafAlloc(TAG_BROKER_FUND, 1L, 20, "broker_fund_account主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_stock", 50000L, 20, "clean_stock主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_trade", 100000L, 20, "clean_trade主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_position", 200000L, 20, "clean_position主键"));
            leafAllocMapper.insert(buildLeafAlloc("clean_asset", 300000L, 20, "clean_asset主键"));
            leafAllocMapper.insert(buildLeafAlloc("event_message", 500000L, 20, "event_message主键"));
            leafAllocMapper.insert(buildLeafAlloc("tx_audit", 900000L, 10, "tx_audit主键"));
        });
    }

    // ============ 工具 ============

    private void executeSql(String sql) { dynamicSqlMapper.executeSql(sql); }

    private void safeAddColumn(String table, String columnDef) {
        try { executeSql("ALTER TABLE " + table + " ADD COLUMN " + columnDef); }
        catch (Exception e) { log.debug("[Schema] 忽略已存在列: {}.{}", table, columnDef.substring(0, columnDef.indexOf(' '))); }
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
}
