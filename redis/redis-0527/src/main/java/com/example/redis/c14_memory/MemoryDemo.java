package com.example.redis.c14_memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 14. 内存管理与淘汰策略
 * <p>
 * Redis 将所有数据存储在内存中，内存管理至关重要。
 * <p>
 * 内存限制：
 * - maxmemory: 最大内存限制
 * - maxmemory-policy: 淘汰策略
 * <p>
 * 过期键删除策略（惰性删除 + 定期删除）：
 * - 惰性删除：访问键时检查是否过期
 * - 定期删除：每 100ms 随机抽取一批键检查
 * <p>
 * 淘汰策略（8 种）：
 * 1. noeviction: 不淘汰，内存满时拒绝写入（默认）
 * 2. allkeys-lru: 在所有键中淘汰最近最少使用的
 * 3. volatile-lru: 仅在设置了过期时间的键中淘汰 LRU
 * 4. allkeys-lfu: 在所有键中淘汰最不常用的（Redis 4.0+）
 * 5. volatile-lfu: 仅在设置了过期时间的键中淘汰 LFU
 * 6. allkeys-random: 随机淘汰
 * 7. volatile-random: 在设置了过期时间的键中随机淘汰
 * 8. volatile-ttl: 淘汰 TTL 最短的键
 * <p>
 * LRU vs LFU：
 * - LRU（Least Recently Used）: 最近最少使用，关注访问时间
 * - LFU（Least Frequently Used）: 最不常用，关注访问频率
 * - Redis 使用近似算法（采样），不是精确 LRU/LFU
 * <p>
 * 内存碎片整理：
 * - activedefrag yes: 开启自动碎片整理
 * - mem-fragmentation-ratio: 内存碎片率
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 内存信息查看
     * <p>
     * INFO memory 返回内存使用详情：
     * - used_memory: 已使用内存（字节）
     * - used_memory_human: 可读格式
     * - used_memory_rss: 操作系统分配的内存
     * - mem_fragmentation_ratio: 碎片率（rss/used）
     * - maxmemory: 最大内存限制
     * - maxmemory_policy: 淘汰策略
     */
    public String memoryInfo() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            Properties info = conn.info("memory");

            String result = String.format(
                    "used=%s, rss=%s, fragmentation_ratio=%s, peak=%s",
                    info.getProperty("used_memory_human"),
                    info.getProperty("used_memory_rss_human"),
                    info.getProperty("mem_fragmentation_ratio"),
                    info.getProperty("used_memory_peak_human")
            );

            log.info("[内存信息] {}", result);

            // 碎片率说明
            String fragRatio = info.getProperty("mem_fragmentation_ratio");
            if (fragRatio != null) {
                double ratio = Double.parseDouble(fragRatio);
                if (ratio > 1.5) {
                    log.warn("[内存碎片] 碎片率过高({})，建议开启 activedefrag", fragRatio);
                } else if (ratio < 1.0) {
                    log.warn("[内存碎片] 碎片率过低({})，可能使用了 swap", fragRatio);
                }
            }

            return result;
        } finally {
            conn.close();
        }
    }

    /**
     * MEMORY 命令
     * <p>
     * MEMORY USAGE key: 估算键占用的内存（字节）
     * MEMORY DOCTOR: 内存诊断建议
     * MEMORY STATS: 内存统计信息
     * MEMORY PURGE: 手动释放内存碎片
     */
    public String memoryCommands() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            // 准备测试数据
            redisTemplate.opsForValue().set("mem:test", "hello world");
            redisTemplate.opsForHash().put("mem:hash", "field1", "value1");
            redisTemplate.opsForHash().put("mem:hash", "field2", "value2");

            // MEMORY USAGE: 估算键内存
            log.info("[MEMORY USAGE] 请通过 redis-cli 执行 MEMORY USAGE 命令查看键内存占用");

            redisTemplate.delete("mem:test");
            redisTemplate.delete("mem:hash");

            return "请通过 redis-cli 执行 MEMORY USAGE 命令";
        } finally {
            conn.close();
        }
    }

    /**
     * 淘汰策略配置说明
     * <p>
     * 生产环境推荐：
     * - 缓存场景: allkeys-lru 或 allkeys-lfu
     * - 混合场景（缓存+持久）: volatile-lru 或 volatile-lfu
     * - 需要精确控制: volatile-ttl
     */
    public String evictionPolicyGuide() {
        String guide =
                "淘汰策略选择指南：\n\n"
                + "1. noeviction（默认）\n"
                + "   - 不淘汰任何键\n"
                + "   - 内存满时新写入返回 OOM 错误\n"
                + "   - 适合：数据不能丢失的持久化场景\n\n"
                + "2. allkeys-lru\n"
                + "   - 在所有键中淘汰 LRU\n"
                + "   - 适合：纯缓存场景（推荐）\n\n"
                + "3. volatile-lru\n"
                + "   - 仅在设置了 TTL 的键中淘汰 LRU\n"
                + "   - 适合：缓存+持久数据混合\n\n"
                + "4. allkeys-lfu\n"
                + "   - 在所有键中淘汰 LFU（Redis 4.0+）\n"
                + "   - 比 LRU 更精确，适合有热点数据的场景\n\n"
                + "5. volatile-lfu\n"
                + "   - 仅在设置了 TTL 的键中淘汰 LFU\n\n"
                + "6. allkeys-random\n"
                + "   - 随机淘汰（性能最好，但不精确）\n\n"
                + "7. volatile-random\n"
                + "   - 在设置了 TTL 的键中随机淘汰\n\n"
                + "8. volatile-ttl\n"
                + "   - 淘汰 TTL 最短的键\n"
                + "   - 适合：有明确过期优先级的场景\n\n"
                + "配置方式：\n"
                + "redis.conf: maxmemory-policy allkeys-lru\n"
                + "运行时: CONFIG SET maxmemory-policy allkeys-lru";

        log.info("[淘汰策略]\n{}", guide);
        return "策略指南已输出";
    }
}
