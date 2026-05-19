package com.example.dynamicds.dto;

public class DataSourceVO {
    private String key;
    private String url;
    private String poolName;
    private int maxPoolSize;
    private int minIdle;
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private boolean online;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getPoolName() { return poolName; }
    public void setPoolName(String poolName) { this.poolName = poolName; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    public int getMinIdle() { return minIdle; }
    public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
    public int getActiveConnections() { return activeConnections; }
    public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }
    public int getIdleConnections() { return idleConnections; }
    public void setIdleConnections(int idleConnections) { this.idleConnections = idleConnections; }
    public int getTotalConnections() { return totalConnections; }
    public void setTotalConnections(int totalConnections) { this.totalConnections = totalConnections; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
}
