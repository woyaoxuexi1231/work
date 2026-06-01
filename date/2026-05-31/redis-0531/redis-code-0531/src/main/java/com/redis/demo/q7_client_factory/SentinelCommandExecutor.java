package com.redis.demo.q7_client_factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Q7: Sentinel 模式的命令执行器.
 *
 * <h3>内部机制</h3>
 * 封装 Lettuce 的 Sentinel 连接：
 * <ul>
 *   <li>LettuceConnectionFactory 内置 TopologyRefreshScheduler</li>
 *   <li>收到 +switch-master 事件时自动切换连接</li>
 *   <li>不像 JedisSentinelPool 那样需要手动管理池生命周期</li>
 * </ul>
 *
 * @see ClusterCommandExecutor
 */
public class SentinelCommandExecutor implements RedisCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(SentinelCommandExecutor.class);

    private final LettuceConnectionFactory connectionFactory;
    private final RedisMode mode = RedisMode.SENTINEL;

    public SentinelCommandExecutor(String sentinelNodes, String masterName, String password) {
        RedisSentinelConfiguration config = new RedisSentinelConfiguration();
        config.setMaster(masterName);
        for (String node : sentinelNodes.split(",")) {
            String[] parts = node.trim().split(":");
            config.sentinel(parts[0], Integer.parseInt(parts[1]));
        }
        config.setPassword(password);

        this.connectionFactory = new LettuceConnectionFactory(config);
        this.connectionFactory.afterPropertiesSet(); //【重点】启动拓扑刷新
        log.info("SentinelCommandExecutor 初始化完成: master={}, sentinels={}",
                masterName, sentinelNodes);
    }

    @Override
    public Object execute(String command, String... args) {
        RedisConnection conn = connectionFactory.getConnection();
        try {
            switch (command.toUpperCase()) {
                case "PING":
                    return conn.ping();
                case "GET":
                    return new String(conn.get(args[0].getBytes()));
                case "SET":
                    conn.set(args[0].getBytes(), args[1].getBytes());
                    return "OK";
                case "INFO":
                    return conn.info(args.length > 0 ? args[0] : "server")
                            .getProperty("redis_version", "unknown");
                default:
                    return "Unsupported command: " + command;
            }
        } finally {
            conn.close(); //【重点】归还连接——Lettuce 单连接复用，无需池化
        }
    }

    @Override
    public boolean isAlive() {
        try {
            return "PONG".equals(execute("PING"));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public RedisMode getMode() { return mode; }

    @Override
    public String getConnectionInfo() {
        return "SentinelCommandExecutor[master="
                + connectionFactory.getSentinelConfiguration().getMaster().getName()
                + "]";
    }

    public void destroy() {
        connectionFactory.destroy();
    }
}
