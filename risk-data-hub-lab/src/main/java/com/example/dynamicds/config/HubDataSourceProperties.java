package com.example.dynamicds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "hub.datasource")
@Data
public class HubDataSourceProperties {

    private String defaultKey;
    private List<Item> items = new ArrayList<>();

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
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private int maxPoolSize = 8;
        private int minIdle = 1;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
    }
}
