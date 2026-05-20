package com.example.dynamicds.dto;

import lombok.Data;

@Data
public class DataSourceConfigDTO {

    private String key;           // 数据源唯一标识
    private String name;          // 页面展示名称
    private String datasourceType; // 数据源类型：TRADE_OMS / TRADE_BROKER / HUB
    private String url;           // JDBC URL
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";

    // HikariCP 连接池参数（均可动态指定，不写死）
    private int maxPoolSize = 10;
    private int minIdle = 2;
    private long connectionTimeout = 30000;
    private long idleTimeout = 600000;
    private long maxLifetime = 1800000;
    private String poolName;
}
