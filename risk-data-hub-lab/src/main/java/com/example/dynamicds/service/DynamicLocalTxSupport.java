package com.example.dynamicds.service;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class DynamicLocalTxSupport {

    private final DynamicDataSourceManager manager;

    public DynamicLocalTxSupport(DynamicDataSourceManager manager) {
        this.manager = manager;
    }

    public <T> T executeOn(String dataSourceKey, SqlCallback<T> callback) {
        DataSource dataSource = manager.getDataSource(dataSourceKey);
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = callback.apply(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("本地事务执行失败, ds=" + dataSourceKey + ", error=" + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    public interface SqlCallback<T> {
        T apply(Connection connection) throws Exception;
    }
}
