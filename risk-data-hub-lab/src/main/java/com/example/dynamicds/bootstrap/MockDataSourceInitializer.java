package com.example.dynamicds.bootstrap;

import com.example.dynamicds.config.HubDataSourceProperties;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.mapper.DynamicSqlMapper;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Mock 数据库初始化 — 创建 trade_oms / trade_broker 库并执行对应 DDL。
 * 全部 SQL 从文件加载，幂等可重复执行。
 */
@Slf4j
@Service
public class MockDataSourceInitializer {

    private static final String SQL_OMS_SCHEMA    = "sql/bootstrap/oms-schema.sql";
    private static final String SQL_BROKER_SCHEMA = "sql/bootstrap/broker-schema.sql";

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
            executeSqlFile(HubConstants.DS_TRADE_OMS, SQL_OMS_SCHEMA);
            executeSqlFile(HubConstants.DS_TRADE_BROKER, SQL_BROKER_SCHEMA);
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
                s.execute("CREATE DATABASE IF NOT EXISTS `" + db + "` DEFAULT CHARACTER SET utf8mb4");
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

    private void executeSqlFile(String dataSourceKey, String classpath) {
        routingMybatisExecutor.run(dataSourceKey, () -> {
            try {
                ClassPathResource resource = new ClassPathResource(classpath);
                String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                int count = 0;
                for (String stmt : sql.split(";")) {
                    String trimmed = stmt.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }
                    dynamicSqlMapper.executeSql(trimmed);
                    count++;
                }
                log.info("[MockDS] 执行 SQL 文件完成: {} → {} ({} 条语句)", dataSourceKey, classpath, count);
            } catch (Exception e) {
                throw new IllegalStateException("执行 SQL 文件失败: " + classpath, e);
            }
        });
    }
}
