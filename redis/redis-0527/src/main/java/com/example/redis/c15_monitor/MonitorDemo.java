package com.example.redis.c15_monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 15. 慢查询与监控
 * <p>
 * Redis 提供丰富的监控和诊断工具。
 * <p>
 * 慢查询日志：
 * - slowlog-log-slower-than: 超过多少微秒记录（默认 10ms）
 * - slowlog-max-len: 最多记录条数（默认 128）
 * - SLOWLOG GET N: 获取最近 N 条慢查询
 * - SLOWLOG LEN: 慢查询日志长度
 * - SLOWLOG RESET: 清空慢查询日志
 * <p>
 * 延迟监控：
 * - LATENCY LATEST: 最新的延迟事件
 * - LATENCY HISTORY event: 某事件的历史延迟
 * - LATENCY RESET [event]: 重置延迟数据
 * - config set latency-monitor-threshold 100: 启用延迟监控（毫秒）
 * <p>
 * INFO 命令模块：
 * - server: 版本、运行时间、PID
 * - clients: 连接数、阻塞数、输入缓冲区
 * - memory: 内存使用、碎片率
 * - stats: 命令执行次数、命中率、过期数
 * - replication: 主从状态、复制偏移量
 * - cpu: CPU 使用
 * - cluster: 集群状态
 * - keyspace: 各库键数量和过期数
 * <p>
 * 客户端管理：
 * - CLIENT LIST: 列出所有连接
 * - CLIENT KILL: 关闭连接
 * - CLIENT SETNAME: 设置连接名称
 * - CLIENT PAUSE: 暂停所有客户端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * INFO 命令 —— 运维第一入口
     * <p>
     * INFO [section] 返回服务器各模块的详细信息。
     * 不指定 section 则返回所有信息。
     */
    public String infoCommand() {
        var conn = redisTemplate.getConnectionFactory().getConnection();

        var serverInfo = conn.info("server");
        var clientsInfo = conn.info("clients");
        var statsInfo = conn.info("stats");

        String result = String.format(
                "version=%s, uptime=%ss, clients=%s, blocked=%s, " +
                        "total_commands=%s, instantaneous_ops=%s, keyspace_hits=%s, keyspace_misses=%s",
                serverInfo.getProperty("redis_version"),
                serverInfo.getProperty("uptime_in_seconds"),
                clientsInfo.getProperty("connected_clients"),
                clientsInfo.getProperty("blocked_clients"),
                statsInfo.getProperty("total_commands_processed"),
                statsInfo.getProperty("instantaneous_ops_per_sec"),
                statsInfo.getProperty("keyspace_hits"),
                statsInfo.getProperty("keyspace_misses")
        );

        log.info("[INFO] {}", result);

        // 命中率计算
        String hits = statsInfo.getProperty("keyspace_hits");
        String misses = statsInfo.getProperty("keyspace_misses");
        if (hits != null && misses != null) {
            long h = Long.parseLong(hits);
            long m = Long.parseLong(misses);
            double hitRate = (h + m) > 0 ? (double) h / (h + m) * 100 : 0;
            log.info("[缓存命中率] {:.2f}%", hitRate);
        }

        return result;
    }

    /**
     * 慢查询日志
     * <p>
     * 慢查询日志记录执行时间超过阈值的命令。
     * 是排查性能问题的重要工具。
     */
    public String slowLog() {
        var conn = redisTemplate.getConnectionFactory().getConnection();

        // SLOWLOG GET: 获取最近的慢查询
        var slowLog = conn.slowLogGet(10);
        log.info("[SLOWLOG] 最近 {} 条慢查询:", slowLog.size());
        for (var entry : slowLog) {
            log.info("  id={}, duration={}μs, args={}",
                    entry.getId(), entry.getDuration(), entry.getArgs());
        }

        // SLOWLOG LEN: 慢查询日志长度
        Long len = conn.slowLogLen();
        log.info("[SLOWLOG] 日志长度: {}", len);

        return "慢查询数=" + slowLog.size();
    }

    /**
     * 客户端连接信息
     * <p>
     * CLIENT LIST 返回所有客户端连接的详细信息：
     * - id: 连接 ID
     * - addr: 客户端地址
     * - db: 当前数据库
     * - sub: 订阅频道数
     * - multi: 事务中命令数
     * - cmd: 最后执行的命令
     * - idle: 空闲时间（秒）
     * - age: 连接存活时间（秒）
     */
    public String clientInfo() {
        var conn = redisTemplate.getConnectionFactory().getConnection();

        // CLIENT LIST
        var clients = conn.getClientList();
        log.info("[CLIENT LIST] 当前连接数: {}", clients.size());
        for (var client : clients) {
            log.info("  id={}, addr={}, db={}, cmd={}, idle={}s",
                    client.get("id"), client.get("addr"),
                    client.get("db"), client.get("cmd"), client.get("idle"));
        }

        return "连接数=" + clients.size();
    }
}
