package com.example.redis.c05_persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 5. 持久化
 * <p>
 * Redis 提供两种持久化方式，可单独使用或混合使用。
 * <p>
 * RDB（Redis Database）：
 * - 快照式持久化，生成某个时间点的全量数据二进制文件
 * - 触发方式：SAVE（阻塞）、BGSAVE（后台 fork 子进程）
 * - 配置触发：save 900 1 / save 300 10 / save 60 10000
 * - 优点：文件小、恢复快、对性能影响小
 * - 缺点：可能丢失最后一次快照后的数据
 * <p>
 * AOF（Append Only File）：
 * - 追加写命令到日志文件
 * - 持久化策略：always（每次写）、everysec（每秒）、no（OS 决定）
 * - 重写：BGREWRITEAOF 压缩 AOF 文件
 * - 优点：数据安全性高（最多丢 1 秒数据）
 * - 缺点：文件大、恢复慢
 * <p>
 * 混合持久化（Redis 4.0+）：
 * - aof-use-rdb-preamble yes
 * - AOF 重写时先写 RDB 格式，再追加增量 AOF
 * - 兼顾 RDB 的快速恢复和 AOF 的数据安全性
 * <p>
 * 生产建议：
 * - 开启 AOF（everysec）+ RDB 备份
 * - Redis 4.0+ 推荐混合持久化
 * - 主节点关闭持久化，从节点开启（减轻主节点压力）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersistenceDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 持久化信息查看
     * <p>
     * 通过 INFO persistence 查看持久化相关状态：
     * - rdb_last_save_time: 最后一次 RDB 保存时间
     * - rdb_last_bgsave_status: 最后一次 BGSAVE 状态
     * - aof_enabled: AOF 是否开启
     * - aof_rewrite_in_progress: AOF 重写是否在进行
     */
    public String persistenceInfo() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        Properties info = conn.info("persistence");

        String result = String.format(
                "RDB: last_save=%s, bgsave_status=%s | AOF: enabled=%s, rewrite=%s",
                info.getProperty("rdb_last_save_time"),
                info.getProperty("rdb_last_bgsave_status"),
                info.getProperty("aof_enabled"),
                info.getProperty("aof_rewrite_in_progress")
        );

        log.info("[持久化信息] {}", result);
        return result;
    }

    /**
     * RDB 手动触发
     * <p>
     * BGSAVE: 后台异步生成 RDB 快照
     * - 使用 fork() 创建子进程
     * - 子进程负责写入 RDB 文件
     * - 主进程继续处理命令
     * - Copy-On-Write 机制减少内存开销
     * <p>
     * 注意：fork 大内存实例时可能短暂阻塞（页表复制）
     */
    public String triggerRdb() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();

        // BGSAVE: 后台保存
        conn.bgSave();
        log.info("[RDB] BGSAVE 已触发");

        // 等待并检查状态
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }

        Properties info = conn.info("persistence");
        String status = info.getProperty("rdb_last_bgsave_status");
        log.info("[RDB] BGSAVE 状态: {}", status);

        return "BGSAVE 状态=" + status;
    }

    /**
     * AOF 相关操作演示
     * <p>
     * BGREWRITEAOF: 触发 AOF 重写
     * AOF 重写会合并冗余命令，压缩 AOF 文件体积
     * <p>
     * 配置说明：
     * - appendonly yes: 开启 AOF
     * - appendfsync everysec: 每秒刷盘（推荐）
     * - auto-aof-rewrite-percentage 100: AOF 文件增长 100% 时触发重写
     * - auto-aof-rewrite-min-size 64mb: AOF 文件最小 64MB 才触发重写
     */
    public String aofOperations() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();

        // BGREWRITEAOF: 触发 AOF 重写
        conn.bgWriteAof();
        log.info("[AOF] BGREWRITEAOF 已触发");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }

        Properties info = conn.info("persistence");
        String result = String.format(
                "aof_enabled=%s, aof_rewrite_in_progress=%s, aof_size=%s",
                info.getProperty("aof_enabled"),
                info.getProperty("aof_rewrite_in_progress"),
                info.getProperty("aof_current_size")
        );

        log.info("[AOF] {}", result);
        return result;
    }
}
