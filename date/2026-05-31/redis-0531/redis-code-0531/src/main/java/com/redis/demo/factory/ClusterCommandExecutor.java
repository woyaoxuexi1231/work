package com.redis.demo.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;

/**
 * Q7: Cluster 模式的命令执行器.
 *
 * <h3>内部机制</h3>
 * 封装 Lettuce 的 Cluster 连接：
 * <ul>
 *   <li>Lettuce 的 StatefulRedisClusterConnection 内置槽位路由</li>
 *   <li>遇到 MOVED/ASK 自动重定向（Smart Routing）</li>
 *   <li>通过 CLUSTER SLOTS 初始化并定期刷新槽位映射表</li>
 * </ul>
 *
 * @see SentinelCommandExecutor
 */
public class ClusterCommandExecutor implements RedisCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ClusterCommandExecutor.class);

    private final LettuceConnectionFactory connectionFactory;
    private final RedisMode mode = RedisMode.CLUSTER;
    private final List<String> clusterNodes;

    public ClusterCommandExecutor(List<String> nodes, String password, int maxRedirects) {
        this.clusterNodes = nodes;

        RedisClusterConfiguration config = new RedisClusterConfiguration();
        for (String node : nodes) {
            String[] parts = node.trim().split(":");
            config.clusterNode(parts[0], Integer.parseInt(parts[1]));
        }
        config.setPassword(password);
        //【重点】maxRedirects——防止 MOVED/ASK 无限重定向
        config.setMaxRedirects(maxRedirects);

        this.connectionFactory = new LettuceConnectionFactory(config);
        this.connectionFactory.afterPropertiesSet();
        log.info("ClusterCommandExecutor 初始化完成: nodes={}, maxRedirects={}",
                nodes, maxRedirects);
    }

    @Override
    public Object execute(String command, String... args) {
        //【重点】使用 Lettuce 集群连接——自动计算槽位 + MOVED/ASK 处理
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
                case "CLUSTER":
                    if ("NODES".equalsIgnoreCase(args.length > 0 ? args[0] : "")) {
                        return conn.execute("CLUSTER", "NODES".getBytes());
                    }
                    return "Unsupported CLUSTER subcommand: " + (args.length > 0 ? args[0] : "");
                default:
                    return "Unsupported command: " + command;
            }
        } finally {
            conn.close();
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
        return "ClusterCommandExecutor[nodes=" + clusterNodes + "]";
    }

    public void destroy() {
        connectionFactory.destroy();
    }
}
