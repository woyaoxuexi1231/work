package com.example.dynamicds.datasource;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * MyBatis 路由执行器 — 在指定数据源上下文中执行 MyBatis 操作。
 * 通过在 ThreadLocal 中设置目标数据源 key，让 DynamicRoutingDataSource 路由到正确的数据库。
 * 使用 try-finally 确保执行后清理 ThreadLocal，避免上下文污染。
 */
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
