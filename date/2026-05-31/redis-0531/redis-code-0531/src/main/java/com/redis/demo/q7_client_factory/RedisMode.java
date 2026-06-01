package com.redis.demo.q7_client_factory;

/**
 * Q7: Redis 运行模式枚举.
 *
 * 对应面试题 Q7 的 RedisMode 枚举设计。
 * 通过此枚举实现 Sentinel/Cluster 两种模式的统一入口。
 */
public enum RedisMode {
    /** 哨兵模式——高可用，自动故障转移 */
    SENTINEL,
    /** 集群模式——数据分片，水平扩展 */
    CLUSTER,
    /** 单机模式——开发测试 */
    STANDALONE
}
