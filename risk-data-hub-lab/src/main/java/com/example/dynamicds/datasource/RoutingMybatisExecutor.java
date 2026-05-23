package com.example.dynamicds.datasource;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * MyBatis 路由执行器 — 在指定数据源上下文中执行 MyBatis 操作。
 * <p>
 * <b>模板方法模式（简化版）</b><br>
 * {@code query()} 定义了"设置上下文 → 执行 → 清理上下文"的固定流程，
 * 调用方只需通过 {@code Supplier<T>} 提供"要执行的数据库操作"。
 * 这避免了在每个业务代码中重复写 try-finally cleanup 的模板代码。
 * <p>
 * <b>为什么 query() 用 {@code Supplier&lt;T&gt;}、run() 用 {@code Runnable}？</b>
 * <ul>
 *   <li>{@code query()} 有返回值（查数据），用 {@code Supplier&lt;T&gt;}（有返回值的函数式接口）。</li>
 *   <li>{@code run()} 没有返回值（增删改），用 {@code Runnable}（无返回值的函数式接口）。</li>
 *   <li>{@code run()} 内部委托给 {@code query()} 并返回 {@code null}，避免逻辑重复。</li>
 * </ul>
 * <p>
 * <b>try-finally 而非 try-with-resources</b><br>
 * ThreadLocal 的清理不是资源释放（没有 Closeable），所以用 try-finally。
 * 即使 action 抛出异常，finally 块中的 clearDataSourceKey() 也会执行，
 * 保证当前线程的 ThreadLocal 不会"脏"，避免后续请求被错误地路由到旧的 key。
 * <p>
 * <b>为什么不做成注解（@RoutingDataSource("trade_oms")）？</b>
 * <ul>
 *   <li>注解方案需要在切面（AOP）中解析注解并设置 ThreadLocal，
 *       会增加项目复杂度和调试难度。</li>
 *   <li>当前显式调用 query/run 的方式调用链一目了然：
 *       在哪个数据源上执行了什么操作。</li>
 *   <li>支持运行时动态决定数据源 key（例如根据数据源类型 switch）。</li>
 *   <li>不需要 AOP 代理，性能更好。</li>
 * </ul>
 */
@Component
public class RoutingMybatisExecutor {

    /**
     * 在指定数据源上执行查询操作（有返回值）。
     * @param dataSourceKey 目标数据源 key
     * @param action 要执行的数据库操作
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
     * 在指定数据源上执行写入操作（无返回值），
     * 内部委托给 query() 实现，避免重复 try-finally。
     */
    public void run(String dataSourceKey, Runnable action) {
        query(dataSourceKey, () -> {
            action.run();
            return null;
        });
    }
}
