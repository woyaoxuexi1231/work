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
 * 动态路由数据源 — Spring 多数据源的核心枢纽。
 * <p>
 * <b>为什么继承 {@link org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource}？</b><br>
 * Spring 提供了 AbstractRoutingDataSource 抽象类作为多数据源路由的标准化方案。
 * 我们只需要重写 {@code determineCurrentLookupKey()} 告诉它"当前线程走哪个库"，
 * 数据源的连接获取、事务绑定、资源释放全部由 Spring 托管。
 * <p>
 * <b>ThreadLocal 的设计</b><br>
 * 使用 {@code static ThreadLocal&lt;String&gt;} 而非方法参数传递数据源 key，原因：
 * <ul>
 *   <li>MyBatis 的 Mapper 接口没有"数据源 key"参数位，无法通过方法参数传递。</li>
 *   <li>ThreadLocal 对上层业务代码透明 — {@link RoutingMybatisExecutor} 设置 key，
 *       MyBatis 内部获取连接时自动路由，业务代码无需关心数据源切换。</li>
 *   <li>方法用 {@code static} 修饰（{@code setDataSourceKey / clearDataSourceKey}），
 *       因为它们是全局路由上下文，不绑定到某个实例。</li>
 * </ul>
 * <p>
 * <b>ReadWriteLock 的设计</b><br>
 * 为什么不是 {@code synchronized} 或单纯的写锁？
 * <ul>
 *   <li>{@code register/remove} 是低频操作（管理后台触发），SQL 查询是高频操作。</li>
 *   <li><b>写锁</b>（注册/删除时）：阻塞所有正在的 {@code determineCurrentLookupKey} 调用，
 *       保证路由表切换瞬间没有查询拿到过期的数据源。</li>
 *   <li><b>读锁</b>：虽然当前代码中读锁没有显式使用（因为 {@code resolvedDataSources}
 *       的读取由 AbstractRoutingDataSource 内部处理），但 ReadWriteLock 保留了未来
 *       加读锁保护的可能性。</li>
 * </ul>
 * <p>
 * <b>为什么 {@code register/remove} 里 {@code new HashMap&lt;&gt;(currentTargets)}？</b><br>
 * {@code AbstractRoutingDataSource.setTargetDataSources()} 期望收到一个新的 {@code Map} 实例，
 * 而不是一个后续会被外部修改的引用。深拷贝防止"外部修改了 Map 但路由表没同步"。
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicRoutingDataSource.class);

    /** 当前线程使用的数据源 key — static 保证全局可见，ThreadLocal 保证线程隔离 */
    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    /** 读写锁 — 保护路由表切换与 SQL 查询不冲突 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public DynamicRoutingDataSource(DataSource defaultDataSource) {
        setDefaultTargetDataSource(defaultDataSource);
        // 初始化空的 targetDataSources，后续通过 DynamicDataSourceManager 动态注册
        setTargetDataSources(new HashMap<>());
        afterPropertiesSet();
    }

    // ---- ThreadLocal 上下文控制 ----

    /** 设置当前线程的数据源 key — 对应 @RoutingMybatisExecutor.query() 的入口 */
    public static void setDataSourceKey(String key) {
        CONTEXT_HOLDER.set(key);
    }

    public static String getDataSourceKey() {
        return CONTEXT_HOLDER.get();
    }

    /** 清理 ThreadLocal 防止内存泄漏 — 在 finally 块中调用 */
    public static void clearDataSourceKey() {
        CONTEXT_HOLDER.remove();
    }

    // ---- AbstractRoutingDataSource 核心方法重写 ----

    @Override
    protected Object determineCurrentLookupKey() {
        String key = CONTEXT_HOLDER.get();
        if (key == null) {
            log.debug("当前线程未设置数据源 key，回退到默认数据源");
        }
        return key;
    }

    // ---- 动态增删入口（由 DynamicDataSourceManager 调用） ----

    /**
     * 注册新数据源并刷新路由表。
     * 写锁保护 + 深拷贝 currentTargets 保证路由表切换的原子性和可见性。
     * 调用 afterPropertiesSet() 让 AbstractRoutingDataSource 重新解析 resolvedDataSources。
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
