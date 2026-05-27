package com.example.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Properties;

/**
 * 数据权限拦截器 - 业务场景1
 *
 * 【业务场景说明】
 * 在多租户SaaS系统中，不同租户的数据需要隔离。
 * 例如：A公司的用户只能看到A公司的订单，B公司的用户只能看到B公司的订单。
 * 通过拦截器自动在SQL中添加租户条件，无需手动在每个SQL中添加WHERE条件。
 *
 * 【实现原理】
 * 拦截StatementHandler的prepare方法，在SQL执行前动态添加租户过滤条件。
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
public class DataPermissionInterceptor implements Interceptor {

    /**
     * 模拟当前登录用户的租户ID（实际项目中从SecurityContext或ThreadLocal中获取）
     */
    private static final ThreadLocal<Long> CURRENT_TENANT_ID = ThreadLocal.withInitial(() -> 1L);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 获取StatementHandler
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        // 获取原始SQL
        BoundSql boundSql = statementHandler.getBoundSql();
        String originalSql = boundSql.getSql();
        log.info("【数据权限拦截器】原始SQL: {}", originalSql);

        // 判断是否需要添加数据权限（实际项目中可根据Mapper方法上的注解决定是否拦截）
        if (shouldIntercept(originalSql)) {
            // 获取当前租户ID
            Long tenantId = CURRENT_TENANT_ID.get();

            // 构建新的SQL，添加租户条件
            String newSql = addTenantCondition(originalSql, tenantId);
            log.info("【数据权限拦截器】添加租户条件后SQL: {}", newSql);

            // 通过反射修改SQL
            metaObject.setValue("delegate.boundSql.sql", newSql);
        }

        // 继续执行原方法
        return invocation.proceed();
    }

    /**
     * 判断是否需要拦截
     * 实际项目中可以检查Mapper方法上是否有@DataPermission注解
     */
    private boolean shouldIntercept(String sql) {
        // 简单演示：对包含特定表的SQL进行拦截
        // 实际项目中可以通过注解、白名单等方式控制
        return sql.toLowerCase().contains("order") || sql.toLowerCase().contains("user");
    }

    /**
     * 添加租户过滤条件
     */
    private String addTenantCondition(String originalSql, Long tenantId) {
        // 简化演示：直接在SQL末尾添加条件
        // 实际项目中需要更复杂的SQL解析（如使用JSqlParser）
        String lowerSql = originalSql.toLowerCase().trim();

        if (lowerSql.startsWith("select")) {
            // SELECT语句：在WHERE后添加租户条件
            if (lowerSql.contains("where")) {
                return originalSql + " AND tenant_id = " + tenantId;
            } else {
                // 没有WHERE子句，需要找到表名后添加
                return originalSql + " WHERE tenant_id = " + tenantId;
            }
        } else if (lowerSql.startsWith("insert")) {
            // INSERT语句：需要在字段列表中添加tenant_id
            // 这里简化处理，实际需要解析SQL结构
            return originalSql;
        }

        return originalSql;
    }

    @Override
    public Object plugin(Object target) {
        // 使用MyBatis提供的工具类包装目标对象
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以接收配置属性
        String tenantId = properties.getProperty("defaultTenantId");
        if (tenantId != null) {
            log.info("【数据权限拦截器】配置的默认租户ID: {}", tenantId);
        }
    }

    /**
     * 设置当前租户ID（实际项目中在登录时调用）
     */
    public static void setCurrentTenantId(Long tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    /**
     * 清除当前租户ID（在请求结束时调用，防止内存泄漏）
     */
    public static void clearCurrentTenantId() {
        CURRENT_TENANT_ID.remove();
    }
}
