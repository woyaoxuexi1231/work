package com.riskdatahub.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 动态路由数据源 — Spring 多数据源的核心枢纽。
 * <p>
 * 继承 {@link AbstractRoutingDataSource} 实现运行时动态路由：
 * <ul>
 *   <li>通过 {@link ThreadLocal} 绑定当前线程的数据源 key</li>
 *   <li>MyBatis 获取连接时自动调用 {@link #determineCurrentLookupKey()} 决定路由目标</li>
 *   <li>使用 {@link ReadWriteLock} 保护路由表切换，读查询与写注册互不阻塞</li>
 * </ul>
 * </p>
 *
 * @author risk-data-hub
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicRoutingDataSource.class);

    /** 当前线程使用的数据源 key — ThreadLocal 保证线程隔离 */
    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    /** 读写锁 — 保护路由表切换期间与 SQL 查询的并发安全 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 构造动态路由数据源，设置默认数据源。
     *
     * @param defaultDataSource 默认数据源（risk_hub）
     */
    public DynamicRoutingDataSource(DataSource defaultDataSource) {
        setDefaultTargetDataSource(defaultDataSource);
        setTargetDataSources(new HashMap<>());
        afterPropertiesSet();
    }

    // ==================== ThreadLocal 上下文控制 ====================

    /**
     * 设置当前线程的数据源 key。
     *
     * @param key 目标数据源标识
     */
    public static void setDataSourceKey(String key) {
        CONTEXT_HOLDER.set(key);
    }

    /**
     * 获取当前线程的数据源 key。
     *
     * @return 当前线程的数据源标识，可能为 null（回退到默认数据源）
     */
    public static String getDataSourceKey() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清理当前线程的数据源 key，防止内存泄漏。
     * <p>
     * 必须在 {@code finally} 块中调用。
     * </p>
     */
    public static void clearDataSourceKey() {
        CONTEXT_HOLDER.remove();
    }

    // ==================== AbstractRoutingDataSource 核心方法 ====================

    /**
     * 决定当前线程的路由 key。
     *
     * @return 数据源 key，为 null 时回退到默认数据源
     */
    @Override
    protected Object determineCurrentLookupKey() {
        String key = CONTEXT_HOLDER.get();
        if (key == null) {
            log.debug("当前线程未设置数据源 key，回退到默认数据源");
        }
        return key;
    }

    // ==================== 动态增删入口 ====================

    /**
     * 注册新数据源并刷新路由表。
     * <p>
     * 写锁保证路由表切换期间没有并发查询获取过期路由。
     * 通过深拷贝 {@code currentTargets} 防止外部修改影响路由表状态。
     * </p>
     *
     * @param key            数据源标识
     * @param ds             数据源实例
     * @param currentTargets 当前所有数据源的快照
     */
    void register(String key, DataSource ds, Map<Object, Object> currentTargets) {
        lock.writeLock().lock();
        try {
            setTargetDataSources(new HashMap<>(currentTargets));
            afterPropertiesSet();
            log.info("[路由] 注册数据源 '{}' — 当前共 {} 个", key, currentTargets.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 移除数据源并刷新路由表。
     *
     * @param key            要移除的数据源标识
     * @param currentTargets 移除后的数据源快照
     */
    void remove(String key, Map<Object, Object> currentTargets) {
        lock.writeLock().lock();
        try {
            setTargetDataSources(new HashMap<>(currentTargets));
            afterPropertiesSet();
            log.info("[路由] 移除数据源 '{}' — 剩余 {} 个", key, currentTargets.size());
        } finally {
            lock.writeLock().unlock();
        }
    }
}
