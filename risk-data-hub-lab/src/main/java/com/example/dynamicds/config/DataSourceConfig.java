package com.example.dynamicds.config;

import com.example.dynamicds.datasource.DynamicRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 注入 DynamicRoutingDataSource 作为主数据源。
 * 默认库只承担兜底角色，真正的业务库都会通过运行时注册接入。
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
