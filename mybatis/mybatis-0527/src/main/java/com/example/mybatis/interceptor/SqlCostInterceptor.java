package com.example.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;

/**
 * SQL执行时间监控拦截器 - 业务场景2
 *
 * 【业务场景说明】
 * 在生产环境中，慢SQL是导致系统性能问题的主要原因之一。
 * 通过拦截器可以：
 * 1. 记录每条SQL的执行时间，便于性能分析
 * 2. 当SQL执行时间超过阈值时，输出警告日志
 * 3. 统计慢SQL的数量，用于监控告警
 * 4. 完整输出SQL（包含参数值），便于问题排查
 *
 * 【实现原理】
 * 拦截StatementHandler的query和update方法，记录执行前后的耗时。
 *
 * @author example
 * @date 2024-01-01
 */
@Slf4j
@Component
@Intercepts({
        @Signature(
                type = StatementHandler.class,
                method = "prepare",
                args = {Connection.class, Integer.class}
        )
})
public class SqlCostInterceptor implements Interceptor {

    /**
     * 慢SQL阈值（毫秒），超过此时间输出警告日志
     */
    private static final long SLOW_SQL_THRESHOLD = 1000;

    /**
     * ThreadLocal存储SQL开始时间
     */
    private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        START_TIME.set(startTime);

        try {
            // 执行原方法
            Object result = invocation.proceed();

            // 计算耗时
            long costTime = System.currentTimeMillis() - startTime;

            // 获取完整SQL（包含参数）
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            String completeSql = getCompleteSql(statementHandler);

            // 输出日志
            if (costTime > SLOW_SQL_THRESHOLD) {
                log.warn("【慢SQL警告】执行耗时: {}ms, SQL: {}", costTime, completeSql);
            } else {
                log.info("【SQL监控】执行耗时: {}ms, SQL: {}", costTime, completeSql);
            }

            return result;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("【SQL异常】执行耗时: {}ms, 异常信息: {}", costTime, e.getMessage());
            throw e;
        } finally {
            // 清理ThreadLocal，防止内存泄漏
            START_TIME.remove();
        }
    }

    /**
     * 获取完整的SQL（包含参数值）
     * 这个方法将PreparedStatement中的占位符?替换为实际的参数值
     */
    private String getCompleteSql(StatementHandler statementHandler) {
        try {
            MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
            BoundSql boundSql = statementHandler.getBoundSql();

            // 获取原始SQL（带占位符?）
            String sql = boundSql.getSql();

            // 获取参数映射
            List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

            // 获取参数对象
            Object parameterObject = boundSql.getParameterObject();

            if (parameterMappings != null && !parameterMappings.isEmpty() && parameterObject != null) {
                // 获取Configuration
                Configuration configuration = (Configuration) metaObject.getValue("delegate.configuration");
                TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();

                if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                    // 单个参数，直接替换
                    sql = sql.replaceFirst("\\?", getParameterValue(parameterObject));
                } else {
                    // 多个参数，逐个替换
                    MetaObject parameterMetaObject = configuration.newMetaObject(parameterObject);
                    for (ParameterMapping parameterMapping : parameterMappings) {
                        String propertyName = parameterMapping.getProperty();
                        if (parameterMetaObject.hasGetter(propertyName)) {
                            Object value = parameterMetaObject.getValue(propertyName);
                            sql = sql.replaceFirst("\\?", getParameterValue(value));
                        } else if (boundSql.hasAdditionalParameter(propertyName)) {
                            Object value = boundSql.getAdditionalParameter(propertyName);
                            sql = sql.replaceFirst("\\?", getParameterValue(value));
                        }
                    }
                }
            }

            // 去除多余空格
            return sql.replaceAll("[\\s]+", " ");
        } catch (Exception e) {
            log.warn("获取完整SQL失败: {}", e.getMessage());
            return statementHandler.getBoundSql().getSql();
        }
    }

    /**
     * 将参数值转换为字符串
     */
    private String getParameterValue(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "'" + obj + "'";
        }
        if (obj instanceof Date) {
            DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.CHINA);
            return "'" + formatter.format(obj) + "'";
        }
        return obj.toString();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以从配置中读取慢SQL阈值
        String threshold = properties.getProperty("slowSqlThreshold");
        if (threshold != null) {
            log.info("【SQL监控拦截器】慢SQL阈值配置: {}ms", threshold);
        }
    }
}
