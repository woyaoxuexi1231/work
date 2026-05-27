package com.example.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
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
 * 【拦截对象选择】
 * MyBatis有4大核心对象可以拦截：
 * 1. Executor: 执行器，管理SQL执行和缓存（最顶层）
 * 2. StatementHandler: 语句处理器，处理SQL预编译和参数设置
 * 3. ParameterHandler: 参数处理器，处理参数映射
 * 4. ResultSetHandler: 结果集处理器，处理返回结果映射
 *
 * 这里拦截的是 StatementHandler，原因：
 * - 此时SQL已经被解析为BoundSql，可以直接获取和修改SQL字符串
 * - 在prepare阶段修改SQL，不会影响后续的参数设置
 * - 比拦截Executor更精准，比拦截ParameterHandler更方便
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

    /**
     * MappedStatement的ID分隔符（用于解析namespace和方法名）
     */
    private static final String MAPPED_STATEMENT_ID_SEPARATOR = ".";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 获取StatementHandler
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        // 获取MappedStatement（包含Mapper接口和方法信息）
        // 注意：StatementHandler的MappedStatement是通过delegate持有的
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");

        // 【核心】根据Mapper方法上的注解决定是否拦截
        if (!shouldIntercept(mappedStatement)) {
            log.debug("【数据权限拦截器】跳过拦截: {}", mappedStatement.getId());
            return invocation.proceed();
        }

        // 获取原始SQL
        BoundSql boundSql = statementHandler.getBoundSql();
        String originalSql = boundSql.getSql();
        log.info("【数据权限拦截器】原始SQL: {}", originalSql);

        // 获取当前租户ID
        Long tenantId = CURRENT_TENANT_ID.get();

        // 获取租户字段名（从注解中读取）
        String tenantColumn = getTenantColumn(mappedStatement);

        // 构建新的SQL，添加租户条件
        String newSql = addTenantCondition(originalSql, tenantId, tenantColumn);
        log.info("【数据权限拦截器】添加租户条件后SQL: {}", newSql);

        // 通过反射修改SQL
        metaObject.setValue("delegate.boundSql.sql", newSql);

        // 继续执行原方法
        return invocation.proceed();
    }

    /**
     * 【核心方法】判断是否需要拦截
     *
     * 检查逻辑：
     * 1. 先检查Mapper方法上是否有@TenantPermission注解
     * 2. 如果方法上没有，再检查Mapper接口（类）上是否有注解
     * 3. 如果都没有注解，默认不拦截（或者根据业务需求改为默认拦截）
     *
     * @param mappedStatement MappedStatement对象
     * @return true: 需要拦截，false: 跳过拦截
     */
    private boolean shouldIntercept(MappedStatement mappedStatement) {
        try {
            // 获取Mapper接口的全限定名和方法名
            String id = mappedStatement.getId();
            String className = id.substring(0, id.lastIndexOf(MAPPED_STATEMENT_ID_SEPARATOR));
            String methodName = id.substring(id.lastIndexOf(MAPPED_STATEMENT_ID_SEPARATOR) + 1);

            // 获取Mapper接口Class
            Class<?> mapperClass = Class.forName(className);

            // 【优先级1】检查方法上的注解
            Method[] methods = mapperClass.getMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    // 匹配参数类型（处理方法重载）
                    if (method.isAnnotationPresent(TenantPermission.class)) {
                        TenantPermission annotation = method.getAnnotation(TenantPermission.class);
                        log.debug("【数据权限拦截器】方法注解 - enabled: {}", annotation.enabled());
                        return annotation.enabled();
                    }
                    break;
                }
            }

            // 【优先级2】检查类上的注解
            if (mapperClass.isAnnotationPresent(TenantPermission.class)) {
                TenantPermission annotation = mapperClass.getAnnotation(TenantPermission.class);
                log.debug("【数据权限拦截器】类注解 - enabled: {}", annotation.enabled());
                return annotation.enabled();
            }

            // 【默认行为】没有注解时不拦截
            // 可以根据业务需求改为 return true（默认拦截）
            return false;

        } catch (Exception e) {
            log.error("【数据权限拦截器】解析注解异常: {}", e.getMessage());
            // 异常时不拦截，保证业务正常运行
            return false;
        }
    }

    /**
     * 获取租户字段名
     *
     * 优先从方法注解获取，其次从类注解获取，最后使用默认值
     */
    private String getTenantColumn(MappedStatement mappedStatement) {
        try {
            String id = mappedStatement.getId();
            String className = id.substring(0, id.lastIndexOf(MAPPED_STATEMENT_ID_SEPARATOR));
            String methodName = id.substring(id.lastIndexOf(MAPPED_STATEMENT_ID_SEPARATOR) + 1);

            Class<?> mapperClass = Class.forName(className);

            // 先查方法注解
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(methodName)) {
                    if (method.isAnnotationPresent(TenantPermission.class)) {
                        return method.getAnnotation(TenantPermission.class).tenantColumn();
                    }
                    break;
                }
            }

            // 再查类注解
            if (mapperClass.isAnnotationPresent(TenantPermission.class)) {
                return mapperClass.getAnnotation(TenantPermission.class).tenantColumn();
            }

        } catch (Exception e) {
            log.error("【数据权限拦截器】获取租户字段异常: {}", e.getMessage());
        }

        return "tenant_id";
    }

    /**
     * 添加租户过滤条件
     *
     * @param originalSql 原始SQL
     * @param tenantId 租户ID
     * @param tenantColumn 租户字段名
     * @return 添加条件后的SQL
     */
    private String addTenantCondition(String originalSql, Long tenantId, String tenantColumn) {
        // 简化演示：直接在SQL末尾添加条件
        // 实际项目中需要使用JSqlParser等SQL解析器进行精确处理
        String lowerSql = originalSql.toLowerCase().trim();

        if (lowerSql.startsWith("select")) {
            // SELECT语句：在WHERE后添加租户条件
            if (lowerSql.contains("where")) {
                return originalSql + " AND " + tenantColumn + " = " + tenantId;
            } else {
                // 没有WHERE子句，需要找到表名后添加
                return originalSql + " WHERE " + tenantColumn + " = " + tenantId;
            }
        } else if (lowerSql.startsWith("insert")) {
            // INSERT语句：需要在字段列表中添加tenant_id
            // 这里简化处理，实际需要解析SQL结构
            return originalSql;
        } else if (lowerSql.startsWith("update")) {
            // UPDATE语句：在WHERE后添加租户条件
            if (lowerSql.contains("where")) {
                return originalSql + " AND " + tenantColumn + " = " + tenantId;
            } else {
                return originalSql + " WHERE " + tenantColumn + " = " + tenantId;
            }
        } else if (lowerSql.startsWith("delete")) {
            // DELETE语句：在WHERE后添加租户条件
            if (lowerSql.contains("where")) {
                return originalSql + " AND " + tenantColumn + " = " + tenantId;
            } else {
                return originalSql + " WHERE " + tenantColumn + " = " + tenantId;
            }
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
