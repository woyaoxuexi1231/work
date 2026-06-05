package com.riskdatahub.datasource;

import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.datasource.dto.DataSourceVO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源管理器 — 运行时管理 HikariCP 连接池的创建、注册、查询和优雅下线。
 * <p>
 * <b>线程安全策略：</b>
 * <ul>
 *   <li>{@link ConcurrentHashMap} — 保证 {@code listAll()}、{@code exists()} 等纯查询方法无锁并发</li>
 *   <li>{@code synchronized} — 保证 {@code register()} 和 {@code remove()} 与路由表刷新的原子性</li>
 * </ul>
 * </p>
 * <p>
 * <b>优雅下线三步走：</b>
 * <ol>
 *   <li>从路由表移除 — 新请求不再路由到此数据源</li>
 *   <li>排空活跃连接 — 轮询等待正在执行的 SQL 完成</li>
 *   <li>关闭连接池 — HikariCP 等待活跃连接自然结束后物理关闭</li>
 * </ol>
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceManager {

    /** 优雅下线最大等待时间（毫秒） */
    private static final long DRAIN_TIMEOUT_MS = 30_000;

    /** 轮询活跃连接数的间隔（毫秒） */
    private static final long DRAIN_POLL_MS = 500;

    private final DynamicRoutingDataSource routingDataSource;

    @Value("${spring.datasource.url}")
    private String hubUrl;

    /**
     * 初始化：注册中台库配置元数据，使其在系统拓扑中可见。
     */
    @PostConstruct
    public void init() {
        putHubConfig(HubConstants.DS_HUB, "中台库", hubUrl);
    }

    /** 数据源 key → HikariCP 连接池 */
    private final ConcurrentHashMap<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();

    /** 数据源 key → 注册配置信息 */
    private final ConcurrentHashMap<String, DataSourceConfigDTO> dataSourceConfigs = new ConcurrentHashMap<>();

    // ==================== 注册数据源 ====================

    /**
     * 注册一个新数据源：创建连接池 → 测试连接 → 加入路由表。
     *
     * @param config 数据源配置（key / url / username / password 等）
     * @throws IllegalArgumentException key 已存在
     * @throws RuntimeException          连接测试失败
     */
    public synchronized void register(DataSourceConfigDTO config) {
        String key = config.getKey();
        if (dataSources.containsKey(key)) {
            throw new IllegalArgumentException("数据源 '" + key + "' 已存在");
        }

        HikariDataSource ds = createHikariDataSource(config);

        try {
            ds.getConnection().close();
            log.info("[数据源管理] 数据源 '{}' 连接测试通过 — {}", key, config.getUrl());
        } catch (Exception e) {
            ds.close();
            log.error("[数据源管理] 数据源 '{}' 连接测试失败: {}", key, e.getMessage());
            throw new RuntimeException("数据源 '" + key + "' 连接测试失败: " + e.getMessage(), e);
        }

        dataSources.put(key, ds);
        dataSourceConfigs.put(key, copyConfig(config));
        routingDataSource.register(key, ds, new ConcurrentHashMap<>(dataSources));
    }

    // ==================== 移除数据源（优雅下线） ====================

    /**
     * 优雅下线一个数据源：摘除路由 → 排空连接 → 关闭连接池。
     *
     * @param key 要移除的数据源标识
     * @throws IllegalArgumentException 数据源不存在
     */
    public synchronized void remove(String key) {
        HikariDataSource ds = dataSources.get(key);
        if (ds == null) {
            throw new IllegalArgumentException("数据源 '" + key + "' 不存在");
        }

        // Step 1: 从路由表移除，新请求不再路由到此数据源
        dataSources.remove(key);
        dataSourceConfigs.remove(key);
        routingDataSource.remove(key, new ConcurrentHashMap<>(dataSources));
        log.info("[数据源管理] 数据源 '{}' 已从路由表移除，开始排空连接...", key);

        // Step 2: 等待活跃连接排空
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < DRAIN_TIMEOUT_MS) {
            int active = getActiveConnections(ds);
            if (active == 0) {
                log.info("[数据源管理] 数据源 '{}' 连接已排空，耗时 {}ms", key, System.currentTimeMillis() - start);
                break;
            }
            log.debug("[数据源管理] 数据源 '{}' 仍有 {} 个活跃连接，继续等待...", key, active);
            try {
                Thread.sleep(DRAIN_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Step 3: 关闭连接池
        int active = getActiveConnections(ds);
        if (active > 0) {
            log.warn("[数据源管理] 数据源 '{}' 强制关闭，仍有 {} 个活跃连接", key, active);
        }
        ds.close();
        log.info("[数据源管理] 数据源 '{}' 连接池已关闭", key);
    }

    // ==================== 查询接口 ====================

    /**
     * 列出所有已注册的数据源。
     *
     * @return 数据源视图列表
     */
    public List<DataSourceVO> listAll() {
        List<DataSourceVO> list = new ArrayList<>();
        for (Map.Entry<String, DataSourceConfigDTO> entry : dataSourceConfigs.entrySet()) {
            HikariDataSource ds = dataSources.get(entry.getKey());
            list.add(toVO(entry.getKey(), ds));
        }
        return list;
    }

    /**
     * 获取指定 key 的数据源视图。
     *
     * @param key 数据源标识
     * @return 数据源视图，不存在时返回 null
     */
    public DataSourceVO get(String key) {
        HikariDataSource ds = dataSources.get(key);
        return toVO(key, ds);
    }

    /**
     * 判断数据源是否已注册。
     *
     * @param key 数据源标识
     * @return true 表示已注册
     */
    public boolean exists(String key) {
        return dataSources.containsKey(key) || dataSourceConfigs.containsKey(key);
    }

    /**
     * 获取数据源配置信息。
     *
     * @param key 数据源标识
     * @return 配置信息的副本，不存在时返回 null
     */
    public DataSourceConfigDTO getConfig(String key) {
        DataSourceConfigDTO config = dataSourceConfigs.get(key);
        return config == null ? null : copyConfig(config);
    }

    /**
     * 获取指定 key 的数据源实例，不存在时返回默认数据源。
     *
     * @param key 数据源标识
     * @return 数据源实例
     */
    public DataSource getDataSource(String key) {
        HikariDataSource ds = dataSources.get(key);
        return ds != null ? ds : routingDataSource;
    }

    /**
     * 获取所有已注册数据源的 key 列表（排序后）。
     *
     * @return 排序后的 key 列表
     */
    public List<String> keys() {
        List<String> keys = new ArrayList<>(dataSourceConfigs.keySet());
        Collections.sort(keys);
        return keys;
    }

    /**
     * 注册中台库配置元数据（不含连接池，中台库由 spring.datasource 管理）。
     *
     * @param key  数据源标识
     * @param name 展示名称
     * @param url  JDBC URL
     */
    public synchronized void putHubConfig(String key, String name, String url) {
        DataSourceConfigDTO cfg = new DataSourceConfigDTO();
        cfg.setKey(key);
        cfg.setName(name);
        cfg.setDatasourceType(HubConstants.TYPE_HUB);
        cfg.setUrl(url);
        dataSourceConfigs.put(key, cfg);
    }

    // ==================== 内部方法 ====================

    /**
     * 根据配置创建 HikariCP 连接池。
     */
    private HikariDataSource createHikariDataSource(DataSourceConfigDTO config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.getUrl());
        hc.setUsername(config.getUsername());
        hc.setPassword(config.getPassword());
        hc.setDriverClassName(config.getDriverClassName());
        hc.setMaximumPoolSize(config.getMaxPoolSize());
        hc.setMinimumIdle(config.getMinIdle());
        hc.setConnectionTimeout(config.getConnectionTimeout());
        hc.setIdleTimeout(config.getIdleTimeout());
        hc.setMaxLifetime(config.getMaxLifetime());
        hc.setPoolName(config.getPoolName() != null ? config.getPoolName() : "HikariPool-" + config.getKey());
        return new HikariDataSource(hc);
    }

    /**
     * 获取 HikariCP 连接池的活跃连接数。
     */
    private int getActiveConnections(HikariDataSource ds) {
        try {
            HikariPoolMXBean mxBean = ds.getHikariPoolMXBean();
            return mxBean != null ? mxBean.getActiveConnections() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 将内部数据源实例转换为视图对象。
     */
    private DataSourceVO toVO(String key, HikariDataSource ds) {
        DataSourceVO vo = new DataSourceVO();
        DataSourceConfigDTO config = dataSourceConfigs.get(key);
        vo.setKey(key);
        vo.setName(config == null ? key : config.getName());
        vo.setDatasourceType(config == null ? "UNKNOWN" : config.getDatasourceType());
        if (ds != null) {
            vo.setUrl(ds.getJdbcUrl());
            vo.setPoolName(ds.getPoolName());
            vo.setMaxPoolSize(ds.getMaximumPoolSize());
            vo.setMinIdle(ds.getMinimumIdle());
            vo.setActiveConnections(getActiveConnections(ds));
            vo.setIdleConnections(getIdleConnections(ds));
            vo.setTotalConnections(getTotalConnections(ds));
            vo.setOnline(!ds.isClosed());
        } else if (config != null) {
            vo.setUrl(config.getUrl());
            vo.setOnline(true);
        }
        return vo;
    }

    private int getIdleConnections(HikariDataSource ds) {
        try {
            HikariPoolMXBean mxBean = ds.getHikariPoolMXBean();
            return mxBean != null ? mxBean.getIdleConnections() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private int getTotalConnections(HikariDataSource ds) {
        try {
            HikariPoolMXBean mxBean = ds.getHikariPoolMXBean();
            return mxBean != null ? mxBean.getTotalConnections() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 深拷贝数据源配置，防止外部修改影响内部状态。
     */
    private DataSourceConfigDTO copyConfig(DataSourceConfigDTO source) {
        DataSourceConfigDTO target = new DataSourceConfigDTO();
        target.setKey(source.getKey());
        target.setName(source.getName());
        target.setDatasourceType(source.getDatasourceType());
        target.setUrl(source.getUrl());
        target.setUsername(source.getUsername());
        target.setPassword(source.getPassword());
        target.setDriverClassName(source.getDriverClassName());
        target.setMaxPoolSize(source.getMaxPoolSize());
        target.setMinIdle(source.getMinIdle());
        target.setConnectionTimeout(source.getConnectionTimeout());
        target.setIdleTimeout(source.getIdleTimeout());
        target.setMaxLifetime(source.getMaxLifetime());
        target.setPoolName(source.getPoolName());
        return target;
    }
}
