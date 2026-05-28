package com.example.redis.c01_core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 1. 核心概念与特性
 * <p>
 * Redis 的核心特征：
 * - 内存存储：所有数据驻留在内存中，读写性能极高（微秒级）
 * - 单线程事件循环：避免上下文切换与锁竞争，命令串行执行天然线程安全
 * - IO 多路复用：单线程同时处理大量连接（epoll/kqueue）
 * - Redis 6.0+ 引入多线程 IO：网络读写由多线程完成，命令执行仍为单线程
 * - RESP 协议：简洁、可读的文本协议，客户端实现简单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreConceptsDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 演示连接与基本信息
     * <p>
     * PING 是最简单的连接测试命令，正常返回 PONG。
     * INFO 命令返回服务器的详细信息，是运维排查的第一入口。
     */
    public String ping() {
        // PING 命令：测试连通性
        String pong = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
        log.info("[PING] → {}", pong);

        // INFO server：获取服务器基本信息
        String info = redisTemplate.getConnectionFactory()
                .getConnection()
                .info("server")
                .getProperty("redis_version");
        log.info("[INFO] Redis 版本: {}", info);

        return "PING → " + pong + " | 版本: " + info;
    }

    /**
     * 演示数据库选择
     * <p>
     * Redis 默认有 16 个数据库（db0 ~ db15），通过 SELECT 命令切换。
     * 不同数据库之间数据完全隔离。
     * 生产环境建议使用不同的 Redis 实例而非多数据库，因为：
     * 1. 多数据库共享同一份内存与连接资源
     * 2. FLUSHDB 会清空当前库，误操作风险高
     * 3. 集群模式下只支持 db0
     */
    public String databaseSelect() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();

        // 写入 db0
        conn.select(0);
        conn.stringCommands().set("db:test".getBytes(), "db0-value".getBytes());
        log.info("[DB0] 写入 db:test = db0-value");

        // 切换到 db1
        conn.select(1);
        conn.stringCommands().set("db:test".getBytes(), "db1-value".getBytes());
        log.info("[DB1] 写入 db:test = db1-value");

        // 验证隔离性
        conn.select(0);
        byte[] val0 = conn.stringCommands().get("db:test".getBytes());
        conn.select(1);
        byte[] val1 = conn.stringCommands().get("db:test".getBytes());

        // 清理测试数据
        conn.select(0);
        conn.keyCommands().del("db:test".getBytes());
        conn.select(1);
        conn.keyCommands().del("db:test".getBytes());
        conn.select(0);

        String result = String.format("db0=%s, db1=%s (互不干扰)", new String(val0), new String(val1));
        log.info("[DB 隔离] {}", result);
        return result;
    }

    /**
     * 演示 Redis 服务器信息查看
     * <p>
     * INFO 命令按模块返回信息：
     * - server：版本、运行时间、PID
     * - clients：连接数、阻塞数
     * - memory：内存使用、碎片率
     * - stats：命令执行次数、命中率
     * - replication：主从状态
     * - cpu：CPU 使用
     * - cluster：集群状态
     * - keyspace：各库键数量
     */
    public String serverInfo() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();

        Properties serverInfo = conn.info("server");
        Properties memoryInfo = conn.info("memory");
        Properties statsInfo = conn.info("stats");

        String result = String.format(
                "版本=%s, 运行时间=%s秒, 内存使用=%s, 命令执行次数=%s",
                serverInfo.getProperty("redis_version"),
                serverInfo.getProperty("uptime_in_seconds"),
                memoryInfo.getProperty("used_memory_human"),
                statsInfo.getProperty("total_commands_processed")
        );
        log.info("[INFO] {}", result);
        return result;
    }
}
