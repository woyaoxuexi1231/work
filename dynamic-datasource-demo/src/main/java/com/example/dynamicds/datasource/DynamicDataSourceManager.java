package com.example.dynamicds.datasource;

import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.dto.DataSourceVO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源管理器 — 核心职责：
 * 1. 运行时注册新数据源（创建 HikariCP 连接池 + 注入路由表）
 * 2. 运行时移除数据源（检测连接状态 → 软关闭 → 硬关闭）
 * 3. 连接池指标查询
 *
 * 线程安全设计：
 * - dataSources 本身是 ConcurrentHashMap，保证 add/remove/get 原子性
 * - register / remove 方法内部对单个 key 的操作有 synchronized 保护，
 *   防止同一 key 被并发注册两次
 * - RoutingDataSource 内部的 targetDataSources 刷新由 ReadWriteLock 保护
 *
 * 优雅下线策略：
 * - 先将数据源从路由表移除（新请求不再路由到此数据源）
 * - 标记为 offline，等待活跃连接排空（HikariCP HikariPoolMXBean 可查）
 * - 超时后强制 close，避免无限等待
 */
@Component
public class DynamicDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceManager.class);

    /** 优雅下线最大等待时间（毫秒） */
    private static final long DRAIN_TIMEOUT_MS = 30_000;
    /** 轮询间隔 */
    private static final long DRAIN_POLL_MS = 500;

    private final DynamicRoutingDataSource routingDataSource;
    private final ConcurrentHashMap<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();

    public DynamicDataSourceManager(DynamicRoutingDataSource routingDataSource) {
        this.routingDataSource = routingDataSource;
    }

    // ==================== 注册数据源 ====================

    /**
     * 动态注册数据源. 连接池参数全部由调用方指定，不写死.
     */
    public synchronized void register(DataSourceConfigDTO config) {
        String key = config.getKey();
        if (dataSources.containsKey(key)) {
            throw new IllegalArgumentException("数据源 '" + key + "' 已存在");
        }

        HikariDataSource ds = createHikariDataSource(config);

        // 测试连接
        try {
            ds.getConnection().close();
            log.info("Datasource '{}' connection test OK — {}", key, config.getUrl());
        } catch (Exception e) {
            ds.close();
            throw new RuntimeException("数据源 '" + key + "' 连接测试失败: " + e.getMessage(), e);
        }

        dataSources.put(key, ds);
        routingDataSource.register(key, ds, new ConcurrentHashMap<>(dataSources));
    }

    // ==================== 移除数据源（优雅下线） ====================

    /**
     * 优雅下线：先从路由表摘除 → 等待活跃连接排空 → 关闭连接池.
     * 如果超时仍有关键连接，强制关闭并记录告警.
     */
    public synchronized void remove(String key) {
        HikariDataSource ds = dataSources.get(key);
        if (ds == null) {
            throw new IllegalArgumentException("数据源 '" + key + "' 不存在");
        }

        // Step 1: 从路由表移除，新请求不再路由到此数据源
        dataSources.remove(key);
        routingDataSource.remove(key, new ConcurrentHashMap<>(dataSources));
        log.info("Datasource '{}' removed from routing table, draining...", key);

        // Step 2: 等待活跃连接排空
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < DRAIN_TIMEOUT_MS) {
            int active = getActiveConnections(ds);
            if (active == 0) {
                log.info("Datasource '{}' drained after {}ms", key, System.currentTimeMillis() - start);
                break;
            }
            log.debug("Datasource '{}' still has {} active connections, waiting...", key, active);
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
            log.warn("Datasource '{}' force closing with {} active connections", key, active);
        }
        ds.close();
        log.info("Datasource '{}' closed", key);
    }

    // ==================== 查询 ====================

    public List<DataSourceVO> listAll() {
        List<DataSourceVO> list = new ArrayList<>();
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            list.add(toVO(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    public DataSourceVO get(String key) {
        HikariDataSource ds = dataSources.get(key);
        if (ds == null) return null;
        return toVO(key, ds);
    }

    public boolean exists(String key) {
        return dataSources.containsKey(key);
    }

    // ==================== 内部方法 ====================

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

    private int getActiveConnections(HikariDataSource ds) {
        try {
            HikariPoolMXBean mxBean = ds.getHikariPoolMXBean();
            return mxBean != null ? mxBean.getActiveConnections() : -1;
        } catch (Exception e) {
            return -1;
        }
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

    private DataSourceVO toVO(String key, HikariDataSource ds) {
        DataSourceVO vo = new DataSourceVO();
        vo.setKey(key);
        vo.setUrl(ds.getJdbcUrl());
        vo.setPoolName(ds.getPoolName());
        vo.setMaxPoolSize(ds.getMaximumPoolSize());
        vo.setMinIdle(ds.getMinimumIdle());
        vo.setActiveConnections(getActiveConnections(ds));
        vo.setIdleConnections(getIdleConnections(ds));
        vo.setTotalConnections(getTotalConnections(ds));
        vo.setOnline(!ds.isClosed());
        return vo;
    }
}
