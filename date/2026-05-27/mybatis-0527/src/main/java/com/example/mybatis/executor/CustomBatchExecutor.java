package com.example.mybatis.executor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.SQLException;
import java.util.List;

/**
 * 自定义Executor演示
 *
 * 【什么是Executor？】
 * Executor是MyBatis的核心组件之一，负责管理SQL的执行。
 * 它是MyBatis四大核心组件之一（Executor、StatementHandler、
 * ParameterHandler、ResultSetHandler）。
 *
 * 【Executor的作用】
 * 1. 管理一级缓存（Local Cache）
 * 2. 执行SQL语句
 * 3. 管理事务
 * 4. 批量处理
 *
 * 【MyBatis内置的Executor类型】
 *
 * 1. SimpleExecutor（默认）
 *    - 每次执行都会创建新的Statement
 *    - 执行完毕后关闭Statement
 *    - 适用于简单场景
 *
 * 2. ReuseExecutor
 *    - 会重用Statement
 *    - 相同的SQL会复用同一个Statement
 *    - 减少Statement创建开销
 *
 * 3. BatchExecutor
 *    - 批量执行SQL
 *    - 将多条SQL合并发送到数据库
 *    - 减少网络往返次数
 *
 * 4. CachingExecutor
 *    - 装饰器模式，在其他Executor基础上增加二级缓存支持
 *
 * 【自定义Executor的使用场景】
 * 1. 统一添加审计字段（create_time, update_time等）
 * 2. 实现软删除逻辑
 * 3. 添加数据加密/解密
 * 4. 实现多租户数据隔离
 * 5. SQL执行监控和统计
 *
 * @author example
 * @date 2024-01-01
 */
@Slf4j
public class CustomBatchExecutor implements Executor {

    /**
     * 被装饰的原始Executor
     */
    private final Executor delegate;

    /**
     * MyBatis配置
     */
    private final Configuration configuration;

    public CustomBatchExecutor(Executor delegate, Configuration configuration) {
        this.delegate = delegate;
        this.configuration = configuration;
    }

    /**
     * 【重要】执行更新操作（INSERT、UPDATE、DELETE）
     *
     * 这是自定义Executor最常重写的方法之一
     * 可以在这里添加：
     * - 审计字段自动填充
     * - 数据校验
     * - 权限检查
     */
    @Override
    public int update(MappedStatement ms, Object parameter) throws SQLException {
        log.info("【自定义Executor】执行更新操作: {}", ms.getId());

        // 可以在这里添加自定义逻辑
        // 例如：自动填充create_time, update_time
        // 例如：添加租户过滤条件
        // 例如：记录操作日志

        long startTime = System.currentTimeMillis();
        try {
            int result = delegate.update(ms, parameter);
            long costTime = System.currentTimeMillis() - startTime;
            log.info("【自定义Executor】更新完成，影响行数: {}, 耗时: {}ms", result, costTime);
            return result;
        } catch (SQLException e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("【自定义Executor】更新异常，耗时: {}ms, 异常: {}", costTime, e.getMessage());
            throw e;
        }
    }

    /**
     * 执行查询操作（基础版本）
     */
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                             ResultHandler resultHandler) throws SQLException {
        log.info("【自定义Executor】执行查询操作: {}", ms.getId());

        long startTime = System.currentTimeMillis();
        try {
            List<E> result = delegate.query(ms, parameter, rowBounds, resultHandler);
            long costTime = System.currentTimeMillis() - startTime;
            log.info("【自定义Executor】查询完成，返回{}条数据, 耗时: {}ms", result.size(), costTime);
            return result;
        } catch (SQLException e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("【自定义Executor】查询异常，耗时: {}ms, 异常: {}", costTime, e.getMessage());
            throw e;
        }
    }

    /**
     * 执行查询操作（带缓存Key和BoundSql版本）
     *
     * 这个方法是Executor接口的抽象方法，必须实现
     * CacheKey用于一级缓存的Key生成
     * BoundSql包含解析后的SQL和参数映射
     */
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                             ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql)
            throws SQLException {
        log.info("【自定义Executor】执行查询操作（带缓存Key）: {}", ms.getId());

        long startTime = System.currentTimeMillis();
        try {
            List<E> result = delegate.query(ms, parameter, rowBounds, resultHandler, cacheKey, boundSql);
            long costTime = System.currentTimeMillis() - startTime;
            log.info("【自定义Executor】查询完成，返回{}条数据, 耗时: {}ms", result.size(), costTime);
            return result;
        } catch (SQLException e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("【自定义Executor】查询异常，耗时: {}ms, 异常: {}", costTime, e.getMessage());
            throw e;
        }
    }

    /**
     * 执行游标查询
     */
    @Override
    public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds)
            throws SQLException {
        log.info("【自定义Executor】执行游标查询: {}", ms.getId());
        return delegate.queryCursor(ms, parameter, rowBounds);
    }

    /**
     * 刷新批处理语句
     */
    @Override
    public List<BatchResult> flushStatements() throws SQLException {
        log.info("【自定义Executor】刷新批处理语句");
        return delegate.flushStatements();
    }

    /**
     * 提交事务
     */
    @Override
    public void commit(boolean required) throws SQLException {
        log.info("【自定义Executor】提交事务, required={}", required);
        delegate.commit(required);
    }

    /**
     * 回滚事务
     */
    @Override
    public void rollback(boolean required) throws SQLException {
        log.info("【自定义Executor】回滚事务, required={}", required);
        delegate.rollback(required);
    }

    /**
     * 关闭Executor
     */
    @Override
    public void close(boolean forceRollback) {
        log.info("【自定义Executor】关闭Executor, forceRollback={}", forceRollback);
        delegate.close(forceRollback);
    }

    /**
     * 判断是否已关闭
     */
    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    /**
     * 获取事务
     */
    @Override
    public Transaction getTransaction() {
        return delegate.getTransaction();
    }

    /**
     * 设置Executor延迟加载的属性
     */
    @Override
    public void setExecutorWrapper(Executor executor) {
        delegate.setExecutorWrapper(executor);
    }

    /**
     * 延迟加载处理
     *
     * 用于支持延迟加载（懒加载）特性
     * 当访问延迟加载的属性时，会调用此方法加载数据
     */
    @Override
    public void deferLoad(MappedStatement ms, MetaObject resultObject, String property,
                          CacheKey cacheKey, Class<?> targetType) {
        log.info("【自定义Executor】延迟加载: {}", property);
        delegate.deferLoad(ms, resultObject, property, cacheKey, targetType);
    }

    /**
     * 创建缓存Key
     *
     * 用于一级缓存的Key生成
     * 相同的CacheKey会被认为是相同的查询
     */
    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameterObject,
                                   RowBounds rowBounds, BoundSql boundSql) {
        return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
    }

    /**
     * 判断是否命中缓存
     */
    @Override
    public boolean isCached(MappedStatement ms, CacheKey key) {
        return delegate.isCached(ms, key);
    }

    /**
     * 清空本地缓存（一级缓存）
     */
    @Override
    public void clearLocalCache() {
        log.info("【自定义Executor】清空本地缓存");
        delegate.clearLocalCache();
    }
}
