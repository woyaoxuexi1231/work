package com.example.dynamicds.datasource;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class RoutingMybatisExecutor {

    public <T> T query(String dataSourceKey, Supplier<T> action) {
        DynamicRoutingDataSource.setDataSourceKey(dataSourceKey);
        try {
            return action.get();
        } finally {
            DynamicRoutingDataSource.clearDataSourceKey();
        }
    }

    public void run(String dataSourceKey, Runnable action) {
        query(dataSourceKey, () -> {
            action.run();
            return null;
        });
    }
}
