package com.example.dynamicds.datasource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class RoutingJdbcExecutor {

    private final JdbcTemplate jdbcTemplate;

    public RoutingJdbcExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public <T> T query(String dataSourceKey, Function<JdbcTemplate, T> action) {
        DynamicRoutingDataSource.setDataSourceKey(dataSourceKey);
        try {
            return action.apply(jdbcTemplate);
        } finally {
            DynamicRoutingDataSource.clearDataSourceKey();
        }
    }

    public void run(String dataSourceKey, Consumer<JdbcTemplate> action) {
        query(dataSourceKey, jdbc -> {
            action.accept(jdbc);
            return null;
        });
    }
}
