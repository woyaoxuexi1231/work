package com.example.dynamicds.config;

import com.example.dynamicds.datasource.DynamicRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 数据源配置 — 以配置中的 hub.datasource.default-key 为启动连接，
 * 后续业务数据源通过 DynamicDataSourceManager 运行时动态注册。
 *
 * 启动时先连 MySQL（不带具体数据库名），自动创建所有 schema 后
 * 再注册 risk_hub / trade_oms / trade_broker 三个业务数据源。
 */
@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {

    private final HubDataSourceProperties properties;

    @Bean
    @Primary
    public DynamicRoutingDataSource dataSource() {
        // 默认数据源改为“无 schema 的管理连接”。
        // 这样即使 risk_hub / trade_oms / trade_broker 还不存在，启动期也能先连上 MySQL 并自动建库。
        HubDataSourceProperties.Item item = properties.findRequired(properties.getDefaultKey());
        HikariDataSource defaultDs = createBootstrapDataSource(item);
        return new DynamicRoutingDataSource(defaultDs);
    }

    private HikariDataSource createBootstrapDataSource(HubDataSourceProperties.Item item) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(toBootstrapUrl(item.getUrl()));
        config.setUsername(item.getUsername());
        config.setPassword(item.getPassword());
        config.setDriverClassName(item.getDriverClassName());
        config.setMaximumPoolSize(item.getMaxPoolSize());
        config.setMinimumIdle(item.getMinIdle());
        config.setConnectionTimeout(item.getConnectionTimeout());
        config.setIdleTimeout(item.getIdleTimeout());
        config.setMaxLifetime(item.getMaxLifetime());
        config.setPoolName("HikariPool-bootstrap-" + item.getKey());
        return new HikariDataSource(config);
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
