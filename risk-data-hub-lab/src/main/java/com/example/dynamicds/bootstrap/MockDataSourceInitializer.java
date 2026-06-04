package com.example.dynamicds.bootstrap;

import com.example.dynamicds.config.HubDataSourceProperties;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.mapper.DynamicSqlMapper;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/**
 * Mock 数据库初始化 — 创建 trade_oms / trade_broker 库、注册数据源、创建表。
 */
@Slf4j
@Service
public class MockDataSourceInitializer {

    private final DynamicDataSourceManager manager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final HubDataSourceProperties properties;
    private final DynamicSqlMapper dynamicSqlMapper;
    private final String hubUrl;
    private final String hubUser;
    private final String hubPwd;

    @Value("${app.ddl.enabled:true}")
    private boolean ddlEnabled;

    public MockDataSourceInitializer(DynamicDataSourceManager manager,
                                      RoutingMybatisExecutor routingMybatisExecutor,
                                      HubDataSourceProperties properties,
                                      DynamicSqlMapper dynamicSqlMapper,
                                      @Value("${spring.datasource.url}") String hubUrl,
                                      @Value("${spring.datasource.username}") String hubUser,
                                      @Value("${spring.datasource.password}") String hubPwd) {
        this.manager = manager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.properties = properties;
        this.dynamicSqlMapper = dynamicSqlMapper;
        this.hubUrl = hubUrl;
        this.hubUser = hubUser;
        this.hubPwd = hubPwd;
    }

    @PostConstruct
    public void init() {
        log.info("[MockDS] 初始化 mock 库, ddl.enabled={}", ddlEnabled);
        ensureTradeSchemas();
        ensureDataSource(HubConstants.DS_TRADE_OMS);
        ensureDataSource(HubConstants.DS_TRADE_BROKER);
        if (ddlEnabled) {
            createOmsTradeTables();
            createBrokerTradeTables();
        }
        log.info("[MockDS] 完成");
    }

    private void ensureTradeSchemas() {
        String prefix = hubUrl.substring(0, hubUrl.indexOf('/', "jdbc:mysql://".length()));
        String query = hubUrl.contains("?") ? hubUrl.substring(hubUrl.indexOf('?')) : "";
        String baseUrl = prefix + query;
        for (String db : java.util.Arrays.asList("trade_oms", "trade_broker")) {
            try (Connection c = DriverManager.getConnection(baseUrl, hubUser, hubPwd);
                 Statement s = c.createStatement()) {
                s.execute("create database if not exists `" + db + "` default character set utf8mb4");
                log.info("[MockDS] schema={}", db);
            } catch (Exception e) {
                throw new IllegalStateException("create schema failed: " + db, e);
            }
        }
    }

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
        log.info("[MockDS] 注册数据源 key={}", key);
        manager.register(config);
    }

    private void createOmsTradeTables() {
        routingMybatisExecutor.run(HubConstants.DS_TRADE_OMS, () -> {
            executeSql("create table if not exists oms_stock_snapshot (id bigint primary key,symbol varchar(16) not null,exchange_code varchar(32),market_day varchar(16) not null,open_price decimal(18,4),high_price decimal(18,4),low_price decimal(18,4),close_price decimal(18,4),volume_qty bigint,turnover_amount decimal(18,2),sync_flag int default 0)");
            executeSql("create table if not exists oms_trade_order (id bigint primary key,order_no varchar(64) not null,stock_code varchar(32) not null,investor_name varchar(128),side_code varchar(8),trade_qty bigint,trade_price decimal(18,4),order_amount decimal(18,2),trade_status varchar(32),trade_time varchar(32),sync_flag int default 0)");
            executeSql("create table if not exists oms_position_holding (id bigint primary key,investor_name varchar(128),stock_code varchar(32) not null,holding_qty bigint,available_qty bigint,cost_price decimal(18,4),market_value decimal(18,2),stat_day varchar(16),sync_flag int default 0)");
            executeSql("create table if not exists oms_cash_asset (id bigint primary key,investor_name varchar(128),account_no varchar(64),cash_balance decimal(18,2),frozen_balance decimal(18,2),total_asset decimal(18,2),stat_day varchar(16),sync_flag int default 0)");
        });
    }

    private void createBrokerTradeTables() {
        routingMybatisExecutor.run(HubConstants.DS_TRADE_BROKER, () -> {
            executeSql("create table if not exists broker_stock_quote (id bigint primary key,quote_code varchar(64) not null,secu_code varchar(32) not null,trade_day varchar(16),exchange_name varchar(32),open_px decimal(18,4),high_px decimal(18,4),low_px decimal(18,4),close_px decimal(18,4),vol_num bigint,turnover_amt decimal(18,2),sync_flag int default 0)");
            executeSql("create table if not exists broker_trade_deal (id bigint primary key,deal_code varchar(64) not null,secu_code varchar(32) not null,client_full_name varchar(128),bs_flag varchar(4),deal_volume bigint,deal_price decimal(18,4),turnover_amount decimal(18,2),status_mark varchar(4),deal_at varchar(32),sync_flag int default 0)");
            executeSql("create table if not exists broker_position_balance (id bigint primary key,client_full_name varchar(128),secu_code varchar(32) not null,current_volume bigint,enable_volume bigint,cost_px decimal(18,4),market_amt decimal(18,2),biz_date varchar(16),sync_flag int default 0)");
            executeSql("create table if not exists broker_fund_account (id bigint primary key,client_full_name varchar(128),fund_account_no varchar(64),current_balance decimal(18,2),frozen_capital decimal(18,2),total_asset decimal(18,2),biz_date varchar(16),sync_flag int default 0)");
        });
    }

    private void executeSql(String sql) { dynamicSqlMapper.executeSql(sql); }
}
