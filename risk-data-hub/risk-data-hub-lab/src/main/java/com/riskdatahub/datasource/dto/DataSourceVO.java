package com.riskdatahub.datasource.dto;

import lombok.Data;

/**
 * 数据源视图对象 — 展示给前端的数据源状态信息。
 * <p>
 * 包含连接池的活跃连接数、空闲连接数、总连接数等运行时指标。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
public class DataSourceVO {

    /** 数据源唯一标识 */
    private String key;

    /** 页面展示名称 */
    private String name;

    /** 数据源类型 */
    private String datasourceType;

    /** JDBC URL */
    private String url;

    /** 连接池名称 */
    private String poolName;

    /** 最大连接数 */
    private Integer maxPoolSize;

    /** 最小空闲连接 */
    private Integer minIdle;

    /** 活跃连接数 */
    private Integer activeConnections;

    /** 空闲连接数 */
    private Integer idleConnections;

    /** 总连接数 */
    private Integer totalConnections;

    /** 是否在线 */
    private Boolean online;
}
