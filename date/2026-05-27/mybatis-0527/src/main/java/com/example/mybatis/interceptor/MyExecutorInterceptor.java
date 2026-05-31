package com.example.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Executor 拦截器示例
 *
 * 【Executor 的职责】
 * Executor 是执行器，负责：
 * 1. 管理执行策略（Simple/Reuse/Batch）
 * 2. 管理一级缓存
 * 3. 管理事务
 *
 * Executor 不负责：
 * - 获取/修改 SQL 字符串（这是 StatementHandler 的职责）
 * - 设置参数（这是 ParameterHandler 的职责）
 * - 处理结果集（这是 ResultSetHandler 的职责）
 *
 * 【拦截 Executor 适合做什么】
 * 1. SQL 执行统计（执行次数、耗时）
 * 2. 审计日志（谁在什么时间执行了什么操作）
 * 3. 权限控制（在执行前判断是否有权限）
 * 4. 多租户隔离（在执行前判断是否允许访问该租户数据）
 *
 * 【拦截 Executor 不适合做什么】
 * - 获取 SQL 字符串（应该拦截 StatementHandler）
 * - 修改 SQL（应该拦截 StatementHandler）
 * - 修改参数（应该拦截 ParameterHandler）
 *
 * @author example
 * @date 2024-01-01
 */
@Slf4j
@Component
@Intercepts({
        @Signature(
                type = Executor.class,
                method = "query",
                /*
                 * args 参数说明：
                 * - MappedStatement: SQL映射信息（包含id、SQL类型、Mapper信息等）
                 * - Object: 查询参数（用户传入的参数）
                 * - RowBounds: 分页参数（offset、limit）
                 * - ResultHandler: 结果处理器（回调接口）
                 */
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
        )
})
public class MyExecutorInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        /*
         * invocation.getArgs() 返回的是被拦截方法的参数列表
         *
         * 因为我们拦截的是 query(MappedStatement, Object, RowBounds, ResultHandler)
         * 所以 args[0] = MappedStatement
         *      args[1] = Object parameter（查询参数）
         *      args[2] = RowBounds（分页参数）
         *      args[3] = ResultHandler（结果处理器）
         */
        Object[] args = invocation.getArgs();

        // 【正规用法】从 MappedStatement 获取元数据信息
        MappedStatement ms = (MappedStatement) args[0];

        // MappedStatement 能提供的信息：
        String mapperId = ms.getId();                    // Mapper方法的全限定名
        SqlCommandType sqlType = ms.getSqlCommandType(); // SQL类型：SELECT/INSERT/UPDATE/DELETE
        String resource = ms.getResource();              // Mapper XML 文件路径

        // 【场景1】执行统计和审计
        log.info("【Executor拦截器】");
        log.info("  Mapper: {}", mapperId);
        log.info("  SQL类型: {}", sqlType);
        log.info("  来源: {}", resource);

        // 【场景2】权限控制示例
        // 判断当前用户是否有权限执行该操作
        // if (!hasPermission(mapperId, sqlType)) {
        //     throw new RuntimeException("无权限执行: " + mapperId);
        // }

        // 【场景3】多租户隔离示例
        // 判断当前用户是否有权访问该Mapper对应的数据
        // if (!canAccessTenant(mapperId)) {
        //     throw new RuntimeException("无权访问: " + mapperId);
        // }

        // 记录执行时间
        long startTime = System.currentTimeMillis();
        try {
            // 执行原方法
            return invocation.proceed();
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.info("  耗时: {}ms", costTime);

            // 【场景4】慢SQL告警
            if (costTime > 1000) {
                log.warn("【慢SQL告警】{} 执行耗时 {}ms", mapperId, costTime);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 接收配置属性
    }
}
