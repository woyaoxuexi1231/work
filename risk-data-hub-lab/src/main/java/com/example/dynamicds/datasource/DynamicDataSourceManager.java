package com.example.dynamicds.datasource;

import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.dto.DataSourceVO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源管理器 — 运行时管理 HikariCP 连接池的核心入口。
 * <p>
 * <b>线程安全：双重锁策略</b><br>
 * 为什么 {@code ConcurrentHashMap + synchronized} 两把锁？
 * <ul>
 *   <li><b>ConcurrentHashMap</b> — 保证 {@code listAll()}、{@code exists()}、{@code getDataSource()} 等
 *       纯查询方法无锁并发，多个线程同时查询数据源列表不会阻塞。</li>
 *   <li><b>synchronized register/remove</b> — 保证同一个 key 不会被并发注册两次。
 *       如果只用 {@code ConcurrentHashMap.putIfAbsent}，注册和路由表刷新不是原子操作：
 *       {@code putIfAbsent} 成功 → 路由表刷新前 → 另一个线程查询路由表会找不到这个数据源。
 *       synchronized 保证了 {@code dataSources.put + routingDataSource.register} 的原子性。</li>
 * </ul>
 * <p>
 * <b>优雅下线三步走</b>
 * <ol>
 *   <li><b>从路由表移除</b> — 新请求通过 {@code determineCurrentLookupKey} 已查不到此 key，
 *       自然路由到默认数据源，不会拿到已关闭的连接。</li>
 *   <li><b>排空活跃连接</b> — 已发起的查询在 30 秒内完成（HikariCP 默认连接超时 30 秒），
 *       通过 {@code HikariPoolMXBean.getActiveConnections()} 轮询等待。</li>
 *   <li><b>强制关闭</b> — 超时后仍有连接在处理中，HikariCP 会等待它们完成后再物理关闭，
 *       {@code close()} 本身不是"暴力杀连接"，而是发送中断信号。</li>
 * </ol>
 * <p>
 * <b>为什么用 HikariPoolMXBean 而不是 {@code connection.isClosed()}？</b><br>
 * {@code connection.isClosed()} 只能判断"连接是否已关闭"，但无法知道"还有多少查询在执行"。
 * HikariPoolMXBean.getActiveConnections() 返回当前正在执行 SQL 的连接数，
 * 这个值降到 0 才说明"没有活跃查询了"，可以安全关闭池。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DynamicDataSourceManager {

    /** 优雅下线最大等待时间（毫秒） */
    private static final long DRAIN_TIMEOUT_MS = 30_000;
    /** 轮询间隔 */
    private static final long DRAIN_POLL_MS = 500;

    private final DynamicRoutingDataSource routingDataSource;
    private final ConcurrentHashMap<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DataSourceConfigDTO> dataSourceConfigs = new ConcurrentHashMap<>();

    // ==================== 注册数据源 ====================

    public synchronized void register(DataSourceConfigDTO config) {
        String key = config.getKey();
        if (dataSources.containsKey(key)) {
            throw new IllegalArgumentException("数据源 '" + key + "' 已存在");
        }

        HikariDataSource ds = createHikariDataSource(config);

        // 测试连接是否可用
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
     * 优雅下线数据源：摘除路由 → 排空连接 → 关闭连接池。
     *
     * 三步策略详见类注释。
     * 注意：
     * - DRAIN_TIMEOUT_MS（30 秒）通常足够等所有查询完成
     * - 如果线程被中断（InterruptedException），立即跳出循环进入强制关闭，
     *   避免 shutdown 钩子被无限阻塞
     * - 即使仍有活跃连接，HikariCP 的 close() 也会等待它们自然结束，
     *   不会中断正在执行的 SQL
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

    // ==================== 查询 ====================

    public List<DataSourceVO> listAll() {
        List<DataSourceVO> list = new ArrayList<>();
        for (Map.Entry<String, DataSourceConfigDTO> entry : dataSourceConfigs.entrySet()) {
            HikariDataSource ds = dataSources.get(entry.getKey());
            list.add(toVO(entry.getKey(), ds));
        }
        return list;
    }

    public DataSourceVO get(String key) {
        HikariDataSource ds = dataSources.get(key);
        return toVO(key, ds);
    }

    public boolean exists(String key) {
        return dataSources.containsKey(key) || dataSourceConfigs.containsKey(key);
    }

    public DataSourceConfigDTO getConfig(String key) {
        DataSourceConfigDTO config = dataSourceConfigs.get(key);
        return config == null ? null : copyConfig(config);
    }

    public DataSource getDataSource(String key) {
        HikariDataSource ds = dataSources.get(key);
        return ds != null ? ds : routingDataSource;
    }

    public List<String> keys() {
        List<String> keys = new ArrayList<>(dataSourceConfigs.keySet());
        Collections.sort(keys);
        return keys;
    }

    /** 注册 hub 配置元数据（不含连接池，hub 由 spring.datasource 管理） */
    public synchronized void putHubConfig(String key, String name, String url) {
        DataSourceConfigDTO cfg = new DataSourceConfigDTO();
        cfg.setKey(key);
        cfg.setName(name);
        cfg.setDatasourceType("HUB");
        cfg.setUrl(url);
        dataSourceConfigs.put(key, cfg);
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
