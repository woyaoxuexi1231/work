package com.example.redis.c07_sentinel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 7. 高可用（哨兵 Sentinel）
 * <p>
 * Sentinel 是 Redis 的高可用解决方案，负责：
 * 1. 监控：检测主从节点是否正常工作
 * 2. 通知：通过 API 通知客户端主节点变更
 * 3. 自动故障转移：主节点故障时自动选举新主节点
 * <p>
 * 核心概念：
 * - 主观下线（SDOWN）：单个 Sentinel 认为节点不可达
 * - 客观下线（ODOWN）：quorum 个 Sentinel 都认为主节点不可达
 * - 领导者选举：Raft 算法选举执行故障转移的 Sentinel
 * - 故障转移：选择最优从节点提升为新主节点
 * <p>
 * 配置示例：
 * sentinel monitor mymaster 127.0.0.1 6379 2
 * sentinel down-after-milliseconds mymaster 5000
 * sentinel failover-timeout mymaster 60000
 * sentinel parallel-syncs mymaster 1
 * <p>
 * 参数说明：
 * - monitor: 监控的主节点名、地址、quorum（至少几个 Sentinel 同意才下线）
 * - down-after-milliseconds: 多少毫秒无响应判定为主观下线
 * - failover-timeout: 故障转移超时时间
 * - parallel-syncs: 故障转移后同时同步的从节点数量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentinelDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * Sentinel 信息查看
     * <p>
     * 通过连接到 Sentinel 节点，使用 SENTINEL 命令获取信息。
     * Spring Boot 配置 Sentinel 模式：
     * spring.redis.sentinel.master=mymaster
     * spring.redis.sentinel.nodes=host1:26379,host2:26379,host3:26379
     */
    public String sentinelInfo() {
        String info =
                "Sentinel 高可用架构说明：\n\n"
                + "1. 部署模式：\n"
                + "   - 至少 3 个 Sentinel 节点（奇数，避免脑裂）\n"
                + "   - Sentinel 与 Redis 实例分开部署\n"
                + "   - 每个 Sentinel 监控所有 Redis 主从节点\n\n"
                + "2. 故障检测流程：\n"
                + "   主节点无响应 -> SDOWN（主观下线）\n"
                + "   -> 询问其他 Sentinel -> ODOWN（客观下线）\n"
                + "   -> Sentinel 领导者选举（Raft）\n"
                + "   -> 执行故障转移\n\n"
                + "3. 故障转移步骤：\n"
                + "   a) 选择最优从节点（优先级->offset->runid）\n"
                + "   b) SLAVEOF NO ONE 提升为新主\n"
                + "   c) 其他从节点 SLAVEOF 新主\n"
                + "   d) 更新 Sentinel 配置\n"
                + "   e) 通知客户端新主地址\n\n"
                + "4. Spring Boot 配置：\n"
                + "   spring.redis.sentinel.master=mymaster\n"
                + "   spring.redis.sentinel.nodes=s1:26379,s2:26379,s3:26379\n"
                + "   spring.redis.sentinel.password=123456";

        log.info("[Sentinel]\n{}", info);
        return "Sentinel 说明已输出";
    }

    /**
     * Sentinel 命令说明
     * <p>
     * 连接到 Sentinel 后可执行的命令：
     * - SENTINEL masters: 查看所有监控的主节点
     * - SENTINEL master <name>: 查看指定主节点信息
     * - SENTINEL slaves <name>: 查看从节点信息
     * - SENTINEL sentinels <name>: 查看其他 Sentinel 信息
     * - SENTINEL get-master-addr-by-name <name>: 获取主节点地址
     * - SENTINEL failover <name>: 手动触发故障转移
     * - SENTINEL ckquorum <name>: 检查 quorum 是否可达
     */
    public String sentinelCommands() {
        String commands =
                "Sentinel 常用命令：\n\n"
                + "# 查看所有主节点\n"
                + "SENTINEL masters\n\n"
                + "# 查看主节点 mymaster 的信息\n"
                + "SENTINEL master mymaster\n\n"
                + "# 获取主节点地址\n"
                + "SENTINEL get-master-addr-by-name mymaster\n\n"
                + "# 查看从节点\n"
                + "SENTINEL replicas mymaster\n\n"
                + "# 查看其他 Sentinel\n"
                + "SENTINEL sentinels mymaster\n\n"
                + "# 手动触发故障转移\n"
                + "SENTINEL failover mymaster\n\n"
                + "# 检查 quorum 是否可达\n"
                + "SENTINEL ckquorum mymaster";

        log.info("[Sentinel 命令]\n{}", commands);
        return "命令列表已输出";
    }
}
