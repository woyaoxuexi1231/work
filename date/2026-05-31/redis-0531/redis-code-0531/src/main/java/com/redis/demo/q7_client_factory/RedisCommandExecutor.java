package com.redis.demo.q7_client_factory;

/**
 * Q7 顶层抽象：Redis 命令执行器.
 *
 * <h3>设计理念</h3>
 * Sentinel 模式和 Cluster 模式返回的是不同类型的连接：
 * <ul>
 *   <li>Sentinel → StatefulRedisConnection（单节点连接）</li>
 *   <li>Cluster → StatefulRedisClusterConnection（集群连接，内置槽位路由）</li>
 * </ul>
 * 通过此接口屏蔽底层差异，上层业务只调用 execute()，不关心底层是哪种模式。
 *
 * <h3>对应面试题 Q7</h3>
 * "如果 Sentinel 和 Cluster 两种客户端分别返回不同类型的连接，
 *  你在外面如何统一封装？" —— 答案就是这个接口。
 */
public interface RedisCommandExecutor {

    /**
     * 执行一个 Redis 命令并返回结果.
     *
     * @param command 命令名称，如 "GET", "SET", "PING"
     * @param args    命令参数
     * @return 执行结果
     * @throws RuntimeException 连接不可用或执行失败时抛出
     */
    Object execute(String command, String... args);

    /**
     * 健康检查——验证底层连接是否可用.
     */
    boolean isAlive();

    /**
     * 当前模式.
     */
    RedisMode getMode();

    /**
     * 获取底层连接信息（用于监控面板展示）.
     */
    String getConnectionInfo();
}
