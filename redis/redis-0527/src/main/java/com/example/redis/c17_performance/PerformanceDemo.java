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
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            // 创建测试大键
            StringBuilder bigValue = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                bigValue.append("x");
            }
            redisTemplate.opsForValue().set("perf:bigkey", bigValue.toString());

            // STRLEN: 字符串长度
            Long len = conn.stringCommands().strLen("perf:bigkey".getBytes());
            log.info("[大键检测] perf:bigkey 字符串长度: {} bytes", len);

            // 大键处理建议
            if (len != null && len > 10240) { // > 10KB
                log.warn("[大键警告] perf:bigkey 是大键，建议拆分或压缩");
            }

            redisTemplate.delete("perf:bigkey");

            return "长度=" + len + "B";
        } finally {
            conn.close();
        }
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
        String tips =
                "Redis 性能优化清单：\n\n"
                + "1. 网络优化\n"
                + "   - 使用 Pipeline 批量操作\n"
                + "   - 使用 MGET/MSET 批量读写\n"
                + "   - 减少不必要的往返\n\n"
                + "2. 命令优化\n"
                + "   - 禁止 KEYS *，使用 SCAN\n"
                + "   - 使用 UNLINK 替代 DEL（大键）\n"
                + "   - 避免 HGETALL（大 Hash）\n"
                + "   - 避免 SMEMBERS（大 Set）\n"
                + "   - 使用 HSCAN/SSCAN 遍历\n\n"
                + "3. 数据结构优化\n"
                + "   - 避免大键（拆分为多个小键）\n"
                + "   - 使用 Hash 替代多个 String\n"
                + "   - 小对象用 ziplist/listpack 编码\n\n"
                + "4. 连接池配置\n"
                + "   Lettuce:\n"
                + "     lettuce.pool.max-active=16\n"
                + "     lettuce.pool.max-idle=8\n"
                + "     lettuce.pool.min-idle=4\n\n"
                + "   Jedis:\n"
                + "     jedis.pool.max-active=16\n"
                + "     jedis.pool.max-idle=8\n"
                + "     jedis.pool.min-idle=4\n\n"
                + "5. 内存优化\n"
                + "   - 设置合理的 maxmemory\n"
                + "   - 选择合适的淘汰策略\n"
                + "   - 开启内存碎片整理\n\n"
                + "6. 持久化优化\n"
                + "   - 主节点关闭持久化\n"
                + "   - 从节点开启 AOF\n"
                + "   - 使用混合持久化\n\n"
                + "7. 监控告警\n"
                + "   - 监控内存使用\n"
                + "   - 监控命中率\n"
                + "   - 监控连接数\n"
                + "   - 慢查询日志\n"
                + "   - 大键检测";

        log.info("[性能优化]\n{}", tips);
        return "优化清单已输出";
    }

    /**
     * redis-benchmark 使用说明
     * <p>
     * redis-benchmark 是 Redis 自带的性能测试工具。
     */
    public String benchmarkGuide() {
        String guide =
                "redis-benchmark 使用指南：\n\n"
                + "# 基础测试\n"
                + "redis-benchmark -h 192.168.3.100 -p 6379 -a 123456\n\n"
                + "# 测试特定命令\n"
                + "redis-benchmark -t set,get -n 100000 -q\n\n"
                + "# Pipeline 测试\n"
                + "redis-benchmark -t set,get -n 100000 -P 16 -q\n\n"
                + "# 指定数据大小\n"
                + "redis-benchmark -t set -n 100000 -d 256 -q\n\n"
                + "# 多线程测试\n"
                + "redis-benchmark -t set,get -n 100000 -c 50 -q\n\n"
                + "# 参数说明：\n"
                + "# -h: 主机\n"
                + "# -p: 端口\n"
                + "# -a: 密码\n"
                + "# -t: 测试的命令类型\n"
                + "# -n: 请求数量\n"
                + "# -c: 并发连接数\n"
                + "# -d: 数据大小（字节）\n"
                + "# -P: Pipeline 大小\n"
                + "# -q: 简洁输出";

        log.info("[Benchmark]\n{}", guide);
        return "Benchmark 指南已输出";
    }
}
