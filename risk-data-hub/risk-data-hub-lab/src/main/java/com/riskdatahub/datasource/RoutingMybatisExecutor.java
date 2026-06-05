package com.riskdatahub.datasource;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * MyBatis 路由执行器 — 在指定数据源上下文中执行 MyBatis 数据库操作。
 * <p>
 * <b>设计目的：</b>封装 ThreadLocal 数据源切换的 try-finally 样板代码，
 * 业务代码只需通过 {@code query()} / {@code run()} 指定数据源 key 和操作逻辑。
 * </p>
 * <p>
 * <b>为什么不用注解（@RoutingDataSource）：</b>
 * <ul>
 *   <li>注解方案需要 AOP 代理，增加项目复杂度</li>
 *   <li>当前显式调用 query/run 的方式调用链一目了然</li>
 *   <li>支持运行时动态决定数据源 key（例如根据数据源类型 switch）</li>
 *   <li>不需要 AOP 代理，性能更好</li>
 * </ul>
 * </p>
 *
 * @author risk-data-hub
 */
@Component
public class RoutingMybatisExecutor {

    /**
     * 在指定数据源上执行查询操作（有返回值）。
     * <p>
     * 自动设置数据源上下文 → 执行业务逻辑 → 清理上下文（恢复到之前的 key）。
     * </p>
     *
     * @param dataSourceKey 目标数据源 key
     * @param action        要执行的数据库操作
     * @param <T>           返回值类型
     * @return 操作结果
     */
    public <T> T query(String dataSourceKey, Supplier<T> action) {
        String previousKey = DynamicRoutingDataSource.getDataSourceKey();
        DynamicRoutingDataSource.setDataSourceKey(dataSourceKey);
        try {
            return action.get();
        } finally {
            if (previousKey == null) {
                DynamicRoutingDataSource.clearDataSourceKey();
            } else {
                DynamicRoutingDataSource.setDataSourceKey(previousKey);
            }
        }
    }

    /**
     * 在指定数据源上执行写入操作（无返回值）。
     * <p>
     * 内部委托给 {@link #query(String, Supplier)} 实现，避免重复 try-finally。
     * </p>
     *
     * @param dataSourceKey 目标数据源 key
     * @param action        要执行的数据库操作
     */
    public void run(String dataSourceKey, Runnable action) {
        query(dataSourceKey, () -> {
            action.run();
            return null;
        });
    }
}
