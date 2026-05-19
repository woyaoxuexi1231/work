package com.example.dynamicds.dto;

public class DataSourceConfigDTO {

    private String key;           // 数据源唯一标识
    private String url;           // JDBC URL
    private String username;
    private String password;
    private String driverClassName = "org.h2.Driver";

    // HikariCP 连接池参数（均可动态指定，不写死）
    private int maxPoolSize = 10;
    private int minIdle = 2;
    private long connectionTimeout = 30000;
    private long idleTimeout = 600000;
    private long maxLifetime = 1800000;
    private String poolName;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }

    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

    public int getMinIdle() { return minIdle; }
    public void setMinIdle(int minIdle) { this.minIdle = minIdle; }

    public long getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }

    public long getIdleTimeout() { return idleTimeout; }
    public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }

    public long getMaxLifetime() { return maxLifetime; }
    public void setMaxLifetime(long maxLifetime) { this.maxLifetime = maxLifetime; }

    public String getPoolName() { return poolName; }
    public void setPoolName(String poolName) { this.poolName = poolName; }
}
