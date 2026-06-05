package com.riskdatahub.config;

import com.riskdatahub.datasource.DynamicRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 数据源配置 — 将 Spring Boot 自动配置的主数据源包装为 {@link DynamicRoutingDataSource}。
 * <p>
 * {@code spring.datasource.*} 配置的主库（risk_hub）作为默认数据源，
 * 其他数据源通过 {@link com.riskdatahub.datasource.DataSourceManager} 在运行时动态注册。
 * </p>
 *
 * @author risk-data-hub
 */
@Configuration
public class DataSourceConfig {

    /**
     * 创建动态路由数据源，以主数据源为默认数据源。
     *
     * @param dsProps Spring Boot 自动注入的数据源属性
     * @return 动态路由数据源（@Primary 确保它成为所有 MyBatis 操作的数据源）
     */
    @Bean
    @Primary
    public DynamicRoutingDataSource dataSource(DataSourceProperties dsProps) {
        HikariDataSource defaultDs = dsProps.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        return new DynamicRoutingDataSource(defaultDs);
    }
}
