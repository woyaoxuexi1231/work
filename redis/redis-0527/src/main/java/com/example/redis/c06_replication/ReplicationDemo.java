package com.example.redis.c06_replication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 6. 复制（主从）
 * <p>
 * Redis 复制实现数据从主节点到从节点的异步复制。
 * <p>
 * 建立主从关系：
 * - 命令: REPLICAOF host port（运行时）
 * - 配置: replicaof host port（配置文件）
 * - 取消: REPLICAOF NO ONE
 * <p>
 * 复制过程：
 * 1. 全量复制（首次连接或积压缓冲区不足时）：
 *    - 从节点发送 PSYNC ? -1
 *    - 主节点返回 FULLRESYNC runid offset
 *    - 主节点执行 BGSAVE 生成 RDB
 *    - 主节点将 RDB 发送给从节点
 *    - 主节点将增量命令发送给从节点
 *    - 从节点加载 RDB 并应用增量命令
 * <p>
 * 2. 部分复制（断线重连时）：
 *    - 从节点发送 PSYNC runid offset
 *    - 主节点检查复制积压缓冲区
 *    - 若 offset 在缓冲区内，返回 CONTINUE + 增量数据
 *    - 若不在，降级为全量复制
 * <p>
 * 复制积压缓冲区（replication backlog）：
 * - 默认 1MB，可通过 repl-backlog-size 配置
 * - 建议设置为：写入速率 × 平均断线时间
 * <p>
 * 复制拓扑：
 * - 一主多从：简单，适合读多写少
 * - 树状复制：减轻主节点压力，但增加延迟
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplicationDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 查看复制信息
     * <p>
     * INFO replication 返回：
     * - role: master / slave
     * - connected_slaves: 连接的从节点数
     * - master_link_status: 主从连接状态
     * - repl_backlog_active: 积压缓冲区是否激活
     * - repl_backlog_size: 积压缓冲区大小
     */
    public String replicationInfo() {
        var conn = redisTemplate.getConnectionFactory().getConnection();
        var info = conn.info("replication");

        String result = String.format(
                "role=%s, connected_slaves=%s, repl_backlog_size=%s",
                info.getProperty("role"),
                info.getProperty("connected_slaves"),
                info.getProperty("repl_backlog_size")
        );

        log.info("[复制信息] {}", result);
        return result;
    }

    /**
     * 复制配置说明
     * <p>
     * 关键配置项：
     * <p>
     * replicaof <masterip> <masterport>
     *   指定主节点地址
     * <p>
     * masterauth <password>
     *   主节点密码（主节点设置了 requirepass 时需要）
     * <p>
     * replica-read-only yes
     *   从节点是否只读（推荐 yes）
     * <p>
     * repl-backlog-size 1mb
     *   复制积压缓冲区大小（建议调大到 256MB+）
     * <p>
     * repl-diskless-sync yes
     *   无盘复制：主节点直接将 RDB 通过 socket 发送给从节点
     *   适合：主从网络带宽大，磁盘 I/O 慢的场景
     * <p>
     * repl-diskless-sync-delay 5
     *   无盘复制延迟：等待更多从节点连接后再发送（减少 RDB 生成次数）
     * <p>
     * min-replicas-to-write 1
     * min-replicas-max-lag 10
     *   最少从节点保护：若连接的从节点少于指定数量，主节点拒绝写入
     */
    public String replicationConfig() {
        String config = """
                主从复制关键配置：

                # 主节点配置
                requirepass 123456
                min-replicas-to-write 1
                min-replicas-max-lag 10

                # 从节点配置
                replicaof 192.168.1.100 6379
                masterauth 123456
                replica-read-only yes

                # 性能优化
                repl-backlog-size 256mb
                repl-diskless-sync yes
                repl-diskless-sync-delay 5

                # 无盘复制适用于：
                # - 磁盘 I/O 慢但网络带宽大的场景
                # - 从节点数量多时，避免多次生成 RDB 文件
                """;

        log.info("[复制配置]\n{}", config);
        return "配置已输出到日志";
    }

    /**
     * 主从切换注意事项
     * <p>
     * 手动切换步骤：
     * 1. 在新主节点执行 REPLICAOF NO ONE
     * 2. 在其他从节点执行 REPLICAOF new-master-ip port
     * 3. 更新客户端连接配置
     * <p>
     * 自动切换（推荐使用 Sentinel）：
     * - Sentinel 自动检测主节点下线
     * - 自动选举新主节点
     * - 自动通知从节点切换
     * - 自动通知客户端新主节点地址
     */
    public String failoverNotes() {
        String notes = """
                主从切换注意事项：

                手动切换：
                1. SLAVEOF NO ONE（新主节点）
                2. SLAVEOF <new-master> <port>（其他从节点）
                3. 更新客户端配置

                自动切换（Sentinel）：
                - sentinel monitor mymaster 127.0.0.1 6379 2
                - sentinel down-after-milliseconds mymaster 5000
                - sentinel failover-timeout mymaster 60000
                """;

        log.info("[主从切换]\n{}", notes);
        return "注意事项已输出";
    }
}
