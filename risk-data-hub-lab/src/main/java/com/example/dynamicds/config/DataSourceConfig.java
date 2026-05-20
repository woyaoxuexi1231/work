package com.example.dynamicds.config;

import com.example.dynamicds.datasource.DynamicRoutingDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {

    private final DataSource defaultDataSource;

    @Bean
    @Primary
    public DynamicRoutingDataSource dataSource() {
        return new DynamicRoutingDataSource(defaultDataSource);
    }
}
