package com.example.dynamicds.config;

import com.example.dynamicds.datasource.DynamicRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 注入 DynamicRoutingDataSource 作为主数据源。
 * 默认库只承担兜底角色，真正的业务库都会通过运行时注册接入。
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DynamicRoutingDataSource dataSource() {
        // 默认数据源只作为 fallback，避免未指定 key 时直接报空指针。
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:fallback_center;DB_CLOSE_DELAY=-1;MODE=MySQL");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("HikariPool-fallback-center");

        HikariDataSource defaultDs = new HikariDataSource(config);

        return new DynamicRoutingDataSource(defaultDs);
    }
}
