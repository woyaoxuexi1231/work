package com.example.redis.c04_typecmds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 4. 各类型核心命令 —— HyperLogLog
 * <p>
 * HyperLogLog 是一种概率数据结构，用于基数统计（集合中不重复元素的数量）。
 * <p>
 * 特性：
 * - 固定内存：无论统计多少元素，只占用 12KB
 * - 标准误差：0.81%
 * - 二进制安全：输入可以是任意字节数组
 * <p>
 * 核心命令：
 * - PFADD key element [element ...]: 添加元素
 * - PFCOUNT key [key ...]: 返回基数估计值
 * - PFMERGE destkey sourcekey [sourcekey ...]: 合并多个 HyperLogLog
 * <p>
 * 对比 Set 统计基数：
 * - Set: 精确统计，内存随元素数量线性增长
 * - HyperLogLog: 近似统计，固定 12KB，适合超大规模去重计数
 * <p>
 * 应用场景：
 * - 网站 UV 统计（每日独立访客）
 * - 搜索去重关键词数
 * - 社交网络独立用户数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyperLogLogDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * HyperLogLog 基础操作
     * <p>
     * PFADD: 添加元素到 HyperLogLog
     * PFCOUNT: 返回基数估计值
     * <p>
     * 注意：PFCOUNT 有 0.81% 的标准误差
     * 对于 100 万个元素，误差约 8100 个
     */
    public String basicOps() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            // 清空
            conn.keyCommands().del("hll:demo".getBytes());

            // PFADD: 添加元素
            // 添加 1000 个唯一元素
            for (int i = 0; i < 1000; i++) {
                conn.hyperLogLogCommands().pfAdd("hll:demo".getBytes(),
                        ("element:" + i).getBytes());
            }

            // PFCOUNT: 获取基数估计值
            Long count = conn.hyperLogLogCommands().pfCount("hll:demo".getBytes());
            log.info("[PFCOUNT] 添加 1000 个唯一元素, 估计值: {}", count);

            // 添加重复元素（不会增加基数）
            for (int i = 0; i < 100; i++) {
                conn.hyperLogLogCommands().pfAdd("hll:demo".getBytes(),
                        ("element:" + i).getBytes());
            }

            count = conn.hyperLogLogCommands().pfCount("hll:demo".getBytes());
            log.info("[PFCOUNT] 再添加 100 个重复元素, 估计值: {}", count);

            conn.keyCommands().del("hll:demo".getBytes());
            return "基数估计=" + count;
        } finally {
            conn.close();
        }
    }

    /**
     * HyperLogLog 应用：UV 统计
     * <p>
     * 每个页面每天维护一个 HyperLogLog
     * 用户访问时 PFADD，统计时 PFCOUNT
     * <p>
     * 内存对比：
     * - Set 存储 1 亿用户 ID: 约 1.5GB
     * - HyperLogLog: 固定 12KB
     */
    public String uvStats() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            conn.keyCommands().del("hll:uv:page:home".getBytes());

            // 模拟用户访问首页
            // 1000 个独立用户访问
            for (int i = 1; i <= 1000; i++) {
                conn.hyperLogLogCommands().pfAdd("hll:uv:page:home".getBytes(),
                        ("user:" + i).getBytes());
            }

            // 部分用户重复访问
            for (int i = 1; i <= 100; i++) {
                conn.hyperLogLogCommands().pfAdd("hll:uv:page:home".getBytes(),
                        ("user:" + i).getBytes());
            }

            Long uv = conn.hyperLogLogCommands().pfCount("hll:uv:page:home".getBytes());
            log.info("[UV统计] 首页独立访客: {} (实际 1000)", uv);

            // PFMERGE: 合并多天 UV
            conn.hyperLogLogCommands().pfAdd("hll:uv:page:home:day1".getBytes(),
                    "user:1".getBytes(), "user:2".getBytes(), "user:3".getBytes());
            conn.hyperLogLogCommands().pfAdd("hll:uv:page:home:day2".getBytes(),
                    "user:2".getBytes(), "user:3".getBytes(), "user:4".getBytes());

            conn.hyperLogLogCommands().pfMerge("hll:uv:page:home:merged".getBytes(),
                    "hll:uv:page:home:day1".getBytes(),
                    "hll:uv:page:home:day2".getBytes());

            Long mergedUv = conn.hyperLogLogCommands().pfCount("hll:uv:page:home:merged".getBytes());
            log.info("[PFMERGE] 两天合并 UV: {} (实际 4)", mergedUv);

            conn.keyCommands().del(
                    "hll:uv:page:home".getBytes(),
                    "hll:uv:page:home:day1".getBytes(),
                    "hll:uv:page:home:day2".getBytes(),
                    "hll:uv:page:home:merged".getBytes()
            );

            return "UV=" + uv + ", 合并UV=" + mergedUv;
        } finally {
            conn.close();
        }
    }
}
