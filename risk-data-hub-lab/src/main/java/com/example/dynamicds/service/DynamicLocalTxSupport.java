package com.example.dynamicds.service;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@Slf4j
@RequiredArgsConstructor
public class DynamicLocalTxSupport {

    private final DynamicDataSourceManager manager;

    public <T> T executeOn(String dataSourceKey, SqlCallback<T> callback) {
        DataSource dataSource = manager.getDataSource(dataSourceKey);
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                log.debug("[本地事务] 开始执行 dataSourceKey={}", dataSourceKey);
                T result = callback.apply(connection);
                connection.commit();
                log.debug("[本地事务] 提交成功 dataSourceKey={}", dataSourceKey);
                return result;
            } catch (Exception e) {
                connection.rollback();
                log.warn("[本地事务] 已回滚 dataSourceKey={}, reason={}", dataSourceKey, e.getMessage());
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
