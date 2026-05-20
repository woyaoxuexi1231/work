package com.example.dynamicds.config;

import com.example.dynamicds.datasource.DynamicRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DynamicRoutingDataSource dataSource(DataSourceProperties dsProps) {
        HikariDataSource defaultDs = dsProps.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        return new DynamicRoutingDataSource(defaultDs);
    }
}
