package com.example.dynamicds.dto;

import lombok.Data;

/**
 * 数据源状态视图（View Object）— CQRS 中的 Query 模型。
 * <p>
 * <b>与 {@link DataSourceConfigDTO}（Command 模型）的区别：</b>
 * <ul>
 *   <li><b>DataSourceConfigDTO</b> — 用于"创建/更新"数据源，包含密码等敏感字段。</li>
 *   <li><b>DataSourceVO</b> — 用于"查看"数据源状态，包含运行指标但不含密码。</li>
 * </ul>
 * <b>分离的原因：</b>
 * <ol>
 *   <li><b>安全</b> — VO 不包含 password 字段，不会意外序列化到前端。</li>
 *   <li><b>职责分离</b> — 活跃连接数等指标来自 HikariPoolMXBean（运行时获取），
 *       不是配置的一部分，放在 VO 中更合适。</li>
 *   <li><b>CQRS 原则</b> — 读模型和写模型各自独立演进。</li>
 * </ol>
 */
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
