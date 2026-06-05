package com.riskdatahub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源配置属性绑定 — 对应 application.yml 中 {@code hub.datasource.*} 配置段。
 * <p>
 * 在启动时由 {@link org.springframework.boot.context.properties.EnableConfigurationProperties} 激活，
 * 运行时通过 {@link com.riskdatahub.controller.DataSourceController} 注册上游数据源。
 * </p>
 *
 * @author risk-data-hub
 */
@ConfigurationProperties(prefix = "hub.datasource")
@Data
public class HubDataSourceProperties {

    /** 所有数据源配置列表 */
    private List<Item> items = new ArrayList<>();

    /**
     * 按 key 查找数据源配置，不存在则抛出异常。
     *
     * @param key 数据源标识
     * @return 匹配的数据源配置
     * @throws IllegalArgumentException 未找到指定 key 的配置
     */
    public Item findRequired(String key) {
        return items.stream()
                .filter(item -> key.equals(item.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未配置数据源: " + key));
    }

    /**
     * 单个数据源的配置项。
     */
    @Data
    public static class Item {
        /** 数据源唯一标识（如 trade_oms） */
        private String key;

        /** 页面展示名称 */
        private String name;

        /** 数据源类型（TRADE_OMS / TRADE_BROKER） */
        private String datasourceType;

        /** JDBC URL */
        private String url;

        /** 数据库用户名 */
        private String username;

        /** 数据库密码 */
        private String password;

        /** JDBC 驱动类名，默认 MySQL 8.x */
        private String driverClassName = "com.mysql.cj.jdbc.Driver";

        /** HikariCP 最大连接数 */
        private int maxPoolSize = 8;

        /** 最小空闲连接 */
        private int minIdle = 1;

        /** 连接超时（毫秒） */
        private long connectionTimeout = 30000;

        /** 空闲超时（毫秒） */
        private long idleTimeout = 600000;

        /** 连接最大存活时间（毫秒） */
        private long maxLifetime = 1800000;
    }
}
