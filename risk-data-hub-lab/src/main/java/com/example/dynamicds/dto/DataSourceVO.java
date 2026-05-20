package com.example.dynamicds.dto;

import lombok.Data;

@Data
public class DataSourceVO {
    private String key;
    private String name;
    private String datasourceType;
    private String url;
    private String poolName;
    private int maxPoolSize;
    private int minIdle;
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private boolean online;
}
