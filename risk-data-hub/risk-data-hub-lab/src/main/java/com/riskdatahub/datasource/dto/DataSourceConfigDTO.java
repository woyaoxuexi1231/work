package com.riskdatahub.datasource.dto;

import lombok.Data;

/**
 * 数据源配置传输对象 — 注册数据源时的请求参数。
 * <p>
 * 包括 JDBC 连接信息和 HikariCP 连接池参数，
 * 所有 HikariCP 参数均有合理的默认值。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
public class DataSourceConfigDTO {

    /** 数据源唯一标识 */
    private String key;

    /** 页面展示名称 */
    private String name;

    /** 数据源类型：TRADE_OMS / TRADE_BROKER / HUB */
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
    private int maxPoolSize = 10;

    /** 最小空闲连接 */
    private int minIdle = 2;

    /** 连接超时（毫秒） */
    private long connectionTimeout = 30000;

    /** 空闲超时（毫秒） */
    private long idleTimeout = 600000;

    /** 连接最大存活时间（毫秒） */
    private long maxLifetime = 1800000;

    /** 连接池名称，默认自动生成 */
    private String poolName;
}
