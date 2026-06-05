package com.riskdatahub.config;

import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 数据源自动注册 — 启动时将 application.yml 中配置的上游数据源注册到路由表。
 * <p>
 * yml 中 {@code hub.datasource.items} 下配置的数据源（trade_oms / trade_broker）
 * 会在应用启动时自动创建连接池并注册到 {@link DataSourceManager}，
 * 无需用户手动调用注册接口。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceAutoRegistrar {

    private final HubDataSourceProperties hubDataSourceProperties;
    private final DataSourceManager dataSourceManager;

    /**
     * 启动时自动注册所有配置的数据源。
     * <p>遍历 hub.datasource.items 中的每一项，转换为 DataSourceConfigDTO 后注册。
     * 注册失败（如数据库不可达）不会阻塞应用启动，仅打印告警日志。</p>
     */
    @PostConstruct
    public void autoRegister() {
        // 遍历配置文件中所有数据源配置
        for (HubDataSourceProperties.Item item : hubDataSourceProperties.getItems()) {
            try {
                // 将 yml 配置项转换为 DataSourceManager 需要的 DTO 格式
                DataSourceConfigDTO config = new DataSourceConfigDTO();
                config.setKey(item.getKey());
                config.setName(item.getName());
                config.setDatasourceType(item.getDatasourceType());
                config.setUrl(item.getUrl());
                config.setUsername(item.getUsername());
                config.setPassword(item.getPassword());
                config.setDriverClassName(item.getDriverClassName());
                config.setMaxPoolSize(item.getMaxPoolSize());
                config.setMinIdle(item.getMinIdle());
                config.setConnectionTimeout(item.getConnectionTimeout());
                config.setIdleTimeout(item.getIdleTimeout());
                config.setMaxLifetime(item.getMaxLifetime());
                // 注册到 DataSourceManager（会创建连接池并加入路由表）
                dataSourceManager.register(config);
                log.info("[数据源自动注册] 数据源 '{}'（{}）注册成功", item.getKey(), item.getName());
            } catch (Exception e) {
                // 注册失败不阻塞应用启动，用户后续可手动注册
                log.warn("[数据源自动注册] 数据源 '{}' 注册失败: {}，可在运行时手动注册", item.getKey(), e.getMessage());
            }
        }
    }
}
