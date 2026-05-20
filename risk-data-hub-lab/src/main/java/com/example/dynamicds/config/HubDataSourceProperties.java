package com.example.dynamicds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源配置绑定 — 对应 application.yml 中 {@code hub.datasource.*}。
 * <p>
 * <b>为什么 Items 字段默认 {@code new ArrayList&lt;&gt;()}？</b><br>
 * Spring Boot 的 ConfigurationProperties 绑定机制中，
 * 如果 yaml 中没有 {@code hub.datasource.items} 配置段，items 默认为 null。
 * 初始化为空 ArrayList 避免了后续遍历时的 NPE。
 * <p>
 * <b>findRequired() vs find() 的命名</b><br>
 * 调用方明确知道 key 必须存在时才用 {@code findRequired()}，不存在时抛出异常快速失败。
 * 如果预期 key 可能不存在，使用 {@code items.stream().filter(...).findFirst()} 自行处理。
 * 这种命名方式（Guava 风格）让 API 的合约更清晰。
 * <p>
 * <b>Item 中字段的默认值</b><br>
 * driverClassName / maxPoolSize / minIdle / connectionTimeout / idleTimeout / maxLifetime
 * 都在字段上直接赋值。调用方创建 DataSourceConfigDTO 时可以只设置关键字段（url/username/password），
 * 其余参数使用默认值。这是"约定优于配置"（Convention over Configuration）的体现。
 */
@ConfigurationProperties(prefix = "hub.datasource")
@Data
public class HubDataSourceProperties {

    /** 默认数据源 key（启动时使用的管理连接） */
    private String defaultKey;
    /** 所有数据源配置列表 */
    private List<Item> items = new ArrayList<>();

    /**
     * 按 key 查找数据源配置，不存在则抛出异常。
     * 相比 getItem(key) 返回 null，findRequired 在关键路径上能更快发现问题。
     */
    public Item findRequired(String key) {
        return items.stream()
                .filter(item -> key.equals(item.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未配置数据源: " + key));
    }

    @Data
    public static class Item {
        private String key;
        private String name;
        private String datasourceType;
        private String url;
        private String username;
        private String password;
        /** 默认 MySQL 8.x 驱动 */
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
