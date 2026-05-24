package com.example.dynamicds.config;

import com.example.dynamicds.datasource.DynamicRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 注入 DynamicRoutingDataSource 作为主数据源.
 * 默认库使用 H2 内存库（仅用于 demo，实际场景通常用 MySQL/PG 作为默认库）。
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DynamicRoutingDataSource dataSource() {
        // 默认数据源 — H2 内存库
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://192.168.3.100:3306/test?useSSL=false&serverTimezone=UTC&characterEncoding=utf-8&allowPublicKeyRetrieval=true");
        config.setUsername("root");
        config.setPassword("123456");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("HikariPool-default");

        HikariDataSource defaultDs = new HikariDataSource(config);

        return new DynamicRoutingDataSource(defaultDs);
    }
}
