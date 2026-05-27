package com.example.redis.c17_performance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 17. 性能优化与运维实践
 * <p>
 * 性能优化的核心原则：
 * 1. 减少网络往返（Pipeline、批量操作）
 * 2. 避免阻塞命令（KEYS、MONITOR、大键操作）
 * 3. 合理使用数据结构（避免大键）
 * 4. 连接池复用
 * 5. 合理设置过期时间
 * <p>
 * 大键诊断：
 * - redis-cli --bigkeys: 扫描所有键找出最大的
 * - MEMORY USAGE key: 估算键内存
 * - OBJECT ENCODING key: 查看底层编码
 * <p>
 * 热键诊断：
 * - MONITOR: 实时查看所有命令（生产慎用！）
 * - CLIENT LIST: 查看连接和最后执行的命令
 * - slowlog-log-slower-than: 慢查询日志
 * <p>
 * 连接池配置：
 * - Lettuce: 基于 Netty，支持异步和响应式
 * - Jedis: 同步阻塞，基于 commons-pool2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 大键检测
     * <p>
     * 大键（Big Key）问题：
     * - 读写大键会阻塞其他命令
     * - 删除大键（DEL）会阻塞（应使用 UNLINK）
     * - 网络传输大键消耗带宽
     * - 内存不均衡
     * <p>
     * 大键定义：
     * - String: 值 > 10KB
     * - Hash/Set/ZSet/List: 元素 > 5000 个或总大小 > 10MB
     */
    public String bigKeyDetection() {
        var conn = redisTemplate.getConnectionFactory().getConnection();

        // 创建测试大键
        StringBuilder bigValue = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            bigValue.append("x");
        }
        redisTemplate.opsForValue().set("perf:bigkey", bigValue.toString());

        // MEMORY USAGE: 估算键内存
        Long usage = conn.memoryUsage("perf:bigkey".getBytes());
        log.info("[大键检测] perf:bigkey 内存占用: {} bytes", usage);

        // STRLEN: 字符串长度
        Long len = conn.stringCommands().strLen("perf:bigkey".getBytes());
        log.info("[大键检测] perf:bigkey 字符串长度: {} bytes", len);

        // 大键处理建议
        if (usage != null && usage > 10240) { // > 10KB
            log.warn("[大键警告] perf:bigkey 是大键，建议拆分或压缩");
        }

        redisTemplate.delete("perf:bigkey");

        return "内存=" + usage + "B, 长度=" + len + "B";
    }

    /**
     * 性能优化建议
     * <p>
     * 1. 使用 Pipeline 批量操作
     * 2. 避免 KEYS，使用 SCAN
     * 3. 合理设置过期时间
     * 4. 使用连接池
     * 5. 避免大键
     * 6. 使用 UNLINK 替代 DEL
     * 7. 合理选择数据结构
     */
    public String performanceTips() {
        String tips = """
                Redis 性能优化清单：

                1. 网络优化
                   - 使用 Pipeline 批量操作
                   - 使用 MGET/MSET 批量读写
                   - 减少不必要的往返

                2. 命令优化
                   - 禁止 KEYS *，使用 SCAN
                   - 使用 UNLINK 替代 DEL（大键）
                   - 避免 HGETALL（大 Hash）
                   - 避免 SMEMBERS（大 Set）
                   - 使用 HSCAN/SSCAN 遍历

                3. 数据结构优化
                   - 避免大键（拆分为多个小键）
                   - 使用 Hash 替代多个 String
                   - 小对象用 ziplist/listpack 编码

                4. 连接池配置
                   Lettuce:
                     lettuce.pool.max-active=16
                     lettuce.pool.max-idle=8
                     lettuce.pool.min-idle=4

                   Jedis:
                     jedis.pool.max-active=16
                     jedis.pool.max-idle=8
                     jedis.pool.min-idle=4

                5. 内存优化
                   - 设置合理的 maxmemory
                   - 选择合适的淘汰策略
                   - 开启内存碎片整理

                6. 持久化优化
                   - 主节点关闭持久化
                   - 从节点开启 AOF
                   - 使用混合持久化

                7. 监控告警
                   - 监控内存使用
                   - 监控命中率
                   - 监控连接数
                   - 慢查询日志
                   - 大键检测
                """;

        log.info("[性能优化]\n{}", tips);
        return "优化清单已输出";
    }

    /**
     * redis-benchmark 使用说明
     * <p>
     * redis-benchmark 是 Redis 自带的性能测试工具。
     */
    public String benchmarkGuide() {
        String guide = """
                redis-benchmark 使用指南：

                # 基础测试
                redis-benchmark -h 192.168.3.100 -p 6379 -a 123456

                # 测试特定命令
                redis-benchmark -t set,get -n 100000 -q

                # Pipeline 测试
                redis-benchmark -t set,get -n 100000 -P 16 -q

                # 指定数据大小
                redis-benchmark -t set -n 100000 -d 256 -q

                # 多线程测试
                redis-benchmark -t set,get -n 100000 -c 50 -q

                # 参数说明：
                # -h: 主机
                # -p: 端口
                # -a: 密码
                # -t: 测试的命令类型
                # -n: 请求数量
                # -c: 并发连接数
                # -d: 数据大小（字节）
                # -P: Pipeline 大小
                # -q: 简洁输出
                """;

        log.info("[Benchmark]\n{}", guide);
        return "Benchmark 指南已输出";
    }
}
