package com.example.redis.c13_pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 13. 管道（Pipeline）与批处理
 * <p>
 * Pipeline 将多个命令打包发送，减少网络往返（RTT）。
 * <p>
 * 普通模式：发送命令 → 等待响应 → 发送下一个命令 → 等待响应
 * Pipeline 模式：发送命令1 → 发送命令2 → ... → 接收所有响应
 * <p>
 * 特性：
 * - 非原子性：Pipeline 中的命令可能穿插其他客户端的命令
 * - 减少 RTT：N 个命令从 N 次往返减少到 1 次
 * - 批量操作优化：大量写入时性能提升显著
 * <p>
 * 对比：
 * - Pipeline: 批量发送，非原子，减少 RTT
 * - 事务（MULTI/EXEC）: 原子执行，也减少 RTT
 * - Lua 脚本: 最强原子性，也减少 RTT
 * <p>
 * 使用建议：
 * - 批量读写大量键时使用 Pipeline
 * - 需要原子性时使用事务或 Lua
 * - Pipeline 的批量大小建议控制在 100~1000 个命令，避免阻塞
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * Pipeline 基础演示
     * <p>
     * 对比普通逐条写入 vs Pipeline 批量写入的性能差异。
     */
    public String pipelinePerformance() {
        int count = 1000;

        // 逐条写入
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            redisTemplate.opsForValue().set("pipe:single:" + i, "value:" + i);
        }
        long singleTime = System.currentTimeMillis() - start;

        // Pipeline 写入
        start = System.currentTimeMillis();
        redisTemplate.executePipelined((RedisConnection connection) -> {
            for (int i = 0; i < count; i++) {
                connection.stringCommands().set(
                        ("pipe:pipeline:" + i).getBytes(),
                        ("value:" + i).getBytes()
                );
            }
            return null;
        });
        long pipelineTime = System.currentTimeMillis() - start;

        log.info("[Pipeline 性能对比] 写入{}条:", count);
        log.info("  逐条写入: {} ms", singleTime);
        log.info("  Pipeline: {} ms", pipelineTime);
        log.info("  性能提升: {}x", singleTime == 0 ? "N/A" : String.format("%.1f", (double) singleTime / pipelineTime));

        // 清理
        for (int i = 0; i < count; i++) {
            redisTemplate.delete("pipe:single:" + i);
            redisTemplate.delete("pipe:pipeline:" + i);
        }

        return String.format("逐条=%dms, Pipeline=%dms", singleTime, pipelineTime);
    }

    /**
     * Pipeline 批量读取
     * <p>
     * MGET 本身支持批量读取，但对于不连续的键或需要复杂操作时，
     * Pipeline 更灵活。
     */
    public String pipelineBatchRead() {
        // 准备数据
        for (int i = 0; i < 100; i++) {
            redisTemplate.opsForValue().set("pipe:read:" + i, "data:" + i);
        }

        // Pipeline 批量读取
        var results = redisTemplate.executePipelined((RedisConnection connection) -> {
            for (int i = 0; i < 100; i++) {
                connection.stringCommands().get(("pipe:read:" + i).getBytes());
            }
            return null;
        });

        log.info("[Pipeline 读取] 批量读取 {} 条数据", results.size());
        log.info("  前3条: {}, {}, {}", results.get(0), results.get(1), results.get(2));

        // 清理
        for (int i = 0; i < 100; i++) {
            redisTemplate.delete("pipe:read:" + i);
        }

        return "读取" + results.size() + "条";
    }

    /**
     * Pipeline 混合操作
     * <p>
     * Pipeline 可以混合不同类型的命令。
     * 注意：Pipeline 中的命令不保证与其他客户端的命令隔离。
     * 若需要原子性，应使用 MULTI/EXEC 或 Lua 脚本。
     */
    public String pipelineMixedOps() {
        var results = redisTemplate.executePipelined((RedisConnection connection) -> {
            // 混合多种操作
            connection.stringCommands().set("pipe:mix:str".getBytes(), "hello".getBytes());
            connection.hashCommands().hSet("pipe:mix:hash".getBytes(), "name".getBytes(), "Redis".getBytes());
            connection.listCommands().rPush("pipe:mix:list".getBytes(), "a".getBytes(), "b".getBytes());
            connection.setCommands().sAdd("pipe:mix:set".getBytes(), "x".getBytes(), "y".getBytes());

            // 读取
            connection.stringCommands().get("pipe:mix:str".getBytes());
            connection.hashCommands().hGet("pipe:mix:hash".getBytes(), "name".getBytes());
            return null;
        });

        log.info("[Pipeline 混合] 执行 {} 个命令", results.size());
        log.info("  str={}, hash={}", results.get(4), results.get(5));

        // 清理
        redisTemplate.delete("pipe:mix:str");
        redisTemplate.delete("pipe:mix:hash");
        redisTemplate.delete("pipe:mix:list");
        redisTemplate.delete("pipe:mix:set");

        return "混合操作完成";
    }
}
