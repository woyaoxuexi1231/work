package com.example.redis.c08_cluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 8. 集群（Redis Cluster）
 * <p>
 * Redis Cluster 是 Redis 的分布式解决方案，提供数据分片与高可用。
 * <p>
 * 核心原理：
 * - 16384 个哈希槽（slot），编号 0~16383
 * - 每个主节点负责一部分槽
 * - CRC16(key) % 16384 计算键属于哪个槽
 * - 节点间通过 Gossip 协议通信
 * <p>
 * 客户端重定向：
 * - MOVED: 键不属于当前节点，永久重定向
 * - ASK: 槽正在迁移，临时重定向
 * <p>
 * 哈希标签（Hash Tag）：
 * - {tag}key: 只对 {} 内的部分计算 hash
 * - 确保相关键分配到同一个槽
 * - 例: user:{1001}:name 和 user:{1001}:age 在同一槽
 * - 用于支持多键操作（事务、Lua 脚本）
 * <p>
 * 集群限制：
 * - 多键操作必须在同一槽（或使用 hash tag）
 * - 不支持跨槽事务
 * - 不支持跨槽 Lua 脚本
 * - 只能使用 db0
 * <p>
 * 扩容缩容：
 * - CLUSTER MEET: 添加新节点
 * - CLUSTER ADDSLOTS: 分配槽
 * - CLUSTER DELSLOTS: 移除槽
 * - 槽迁移: CLUSTER SETSLOT MIGRATING/IMPORTING
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 集群信息查看
     * <p>
     * INFO cluster 返回集群状态。
     * CLUSTER INFO 返回详细的集群信息。
     * CLUSTER NODES 返回所有节点信息。
     */
    public String clusterInfo() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            Properties info = conn.info("cluster");

            String result = String.format(
                    "cluster_enabled=%s",
                    info.getProperty("cluster_enabled")
            );

            log.info("[集群信息] {}", result);

            // 集群配置说明
            String clusterConfig =
                "Redis Cluster 关键配置：\n\n"
                + "cluster-enabled yes\n"
                + "cluster-config-file nodes.conf\n"
                + "cluster-node-timeout 15000\n"
                + "cluster-require-full-coverage yes\n"
                + "cluster-migration-barrier 1\n\n"
                + "# cluster-node-timeout:\n"
                + "#   节点超时时间（毫秒），超时后判定为 PFAIL\n"
                + "#   建议 15000~30000\n\n"
                + "# cluster-require-full-coverage:\n"
                + "#   是否要求所有槽都被覆盖才提供服务\n"
                + "#   no: 部分槽不可用时其他槽仍可服务\n\n"
                + "# cluster-migration-barrier:\n"
                + "#   从节点迁移屏障\n"
                + "#   主节点至少有 N 个从节点时，多余的从节点才会迁移到孤儿主节点";

        log.info("[集群配置]\n{}", clusterConfig);
        return result;
        } finally {
            conn.close();
        }
    }

    /**
     * 哈希标签演示
     * <p>
     * 通过 {tag} 确保相关键分配到同一个槽。
     * 这是集群模式下实现多键操作的关键。
     */
    public String hashTag() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        // 使用 hash tag 确保同槽
        // {user:1001} 是 hash tag，只有这部分参与 hash 计算
        ops.set("user:{1001}:name", "张三");
        ops.set("user:{1001}:age", "28");
        ops.set("user:{1001}:email", "zhangsan@example.com");

        String name = ops.get("user:{1001}:name");
        String age = ops.get("user:{1001}:age");
        log.info("[Hash Tag] name={}, age={} (在同一槽中)", name, age);

        // 不使用 hash tag 的键可能在不同槽
        // ops.set("user:1001:name", "张三");  // 可能在槽 A
        // ops.set("user:1001:age", "28");     // 可能在槽 B

        redisTemplate.delete("user:{1001}:name");
        redisTemplate.delete("user:{1001}:age");
        redisTemplate.delete("user:{1001}:email");

        return "name=" + name + ", age=" + age;
    }

    /**
     * 集群管理命令说明
     * <p>
     * 使用 redis-cli --cluster 工具管理集群。
     */
    public String clusterManagement() {
        String commands =
                "集群管理命令：\n\n"
                + "# 创建集群（6 节点：3 主 3 从）\n"
                + "redis-cli --cluster create \\\n"
                + "  192.168.1.101:6379 192.168.1.102:6379 192.168.1.103:6379 \\\n"
                + "  192.168.1.104:6379 192.168.1.105:6379 192.168.1.106:6379 \\\n"
                + "  --cluster-replicas 1\n\n"
                + "# 查看集群信息\n"
                + "redis-cli --cluster check 192.168.1.101:6379\n\n"
                + "# 查看集群节点\n"
                + "redis-cli -h 192.168.1.101 -p 6379 CLUSTER NODES\n\n"
                + "# 添加新节点\n"
                + "redis-cli --cluster add-node 192.168.1.107:6379 192.168.1.101:6379\n\n"
                + "# 重新分片（迁移槽）\n"
                + "redis-cli --cluster reshard 192.168.1.101:6379\n\n"
                + "# 删除节点\n"
                + "redis-cli --cluster del-node 192.168.1.101:6379 <node-id>\n\n"
                + "# 修复集群\n"
                + "redis-cli --cluster fix 192.168.1.101:6379";

        log.info("[集群管理]\n{}", commands);
        return "管理命令已输出";
    }
}
