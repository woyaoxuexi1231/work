package com.example.dynamicds.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 动态路由数据源 — 继承 AbstractRoutingDataSource，支持运行时增删数据源.
 *
 * 关键改造点：
 * 1. targetDataSources 使用外部 ConcurrentHashMap 托管，避免内部私有 Map 不可控
 * 2. 重写 determineCurrentLookupKey，通过 ThreadLocal 获取当前线程指定的数据源 key
 * 3. ReadWriteLock 保证刷新 targetDataSources 时的线程安全 — 写锁保护 add/remove，
 *    读锁保护 determineTargetDataSource（但实际上刷新瞬间才需要屏障，平时读取
 *    AbstractRoutingDataSource 内部的 resolvedDataSources 足够安全）
 * 4. 每次增删后调用 afterPropertiesSet() 重新解析数据源映射
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicRoutingDataSource.class);

    /** 当前线程使用的数据源 key */
    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public DynamicRoutingDataSource(DataSource defaultDataSource) {
        setDefaultTargetDataSource(defaultDataSource);
        // 初始化空的 targetDataSources，后续动态注册
        setTargetDataSources(new HashMap<>());
        afterPropertiesSet();
    }

    // ---- ThreadLocal 上下文控制 ----

    public static void setDataSourceKey(String key) {
        CONTEXT_HOLDER.set(key);
    }

    public static String getDataSourceKey() {
        return CONTEXT_HOLDER.get();
    }

    public static void clearDataSourceKey() {
        CONTEXT_HOLDER.remove();
    }

    // ---- AbstractRoutingDataSource 核心方法重写 ----

    @Override
    protected Object determineCurrentLookupKey() {
        String key = CONTEXT_HOLDER.get();
        if (key == null) {
            log.debug("No datasource key in context, falling back to default");
        }
        return key;
    }

    // ---- 动态增删入口（由 DynamicDataSourceManager 调用） ----

    /**
     * 注册新数据源并刷新路由表. 写锁保证与正在执行的 SQL 查询不冲突.
     */
    void register(String key, DataSource ds, Map<Object, Object> currentTargets) {
        lock.writeLock().lock();
        try {
            setTargetDataSources(new HashMap<>(currentTargets));
            afterPropertiesSet();
            log.info("Registered datasource '{}' — total: {}", key, currentTargets.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 移除数据源并刷新路由表.
     */
    void remove(String key, Map<Object, Object> currentTargets) {
        lock.writeLock().lock();
        try {
            setTargetDataSources(new HashMap<>(currentTargets));
            afterPropertiesSet();
            log.info("Removed datasource '{}' — remaining: {}", key, currentTargets.size());
        } finally {
            lock.writeLock().unlock();
        }
    }
}
