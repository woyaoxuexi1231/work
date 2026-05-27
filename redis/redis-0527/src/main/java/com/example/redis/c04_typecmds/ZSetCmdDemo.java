package com.example.redis.c04_typecmds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 4. 各类型核心命令 —— Sorted Set（有序集合）
 * <p>
 * Sorted Set = Set + Score（分数），每个成员关联一个浮点数分数。
 * 成员唯一，分数可重复。按分数从小到大排序。
 * <p>
 * 底层编码：
 * - ziplist/listpack: 元素少且成员短时（≤ zset-max-listpack-entries，128）
 * - skiplist + dict: 其他情况
 *   - skiplist: 支持范围查询 O(logN)
 *   - dict: 支持按成员查分数 O(1)
 * <p>
 * 核心命令：
 * - 写入：ZADD
 * - 删除：ZREM
 * - 查询：ZSCORE、ZREVRANK、ZRANK
 * - 范围：ZRANGE、ZREVRANGE、ZRANGEBYSCORE、ZREVRANGEBYSCORE
 * - 计数：ZCOUNT
 * - 自增：ZINCRBY
 * - 集合运算：ZUNIONSTORE、ZINTERSTORE
 * - 弹出：ZPOPMIN、ZPOPMAX、BZPOPMIN、BZPOPMAX
 * <p>
 * 应用场景：
 * - 排行榜：分数为积分，ZRANGE 获取 Top N
 * - 延迟队列：分数为执行时间戳，ZRANGEBYSCORE 获取到期任务
 * - 权重优先级队列：分数为优先级
 * - 时间窗口限流：分数为时间戳，ZCOUNT 统计窗口内请求数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZSetCmdDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * ZSet 基础操作
     * <p>
     * ZADD: 添加成员及分数（已存在则更新分数）
     * ZSCORE: 获取成员分数
     * ZRANK: 获取成员排名（升序，从 0 开始）
     * ZREVRANK: 获取成员排名（降序）
     * ZCARD: 成员总数
     */
    public String basicOps() {
        var ops = redisTemplate.opsForZSet();

        // ZADD: 添加成员
        ops.add("zset:leaderboard", "Alice", 95.5);
        ops.add("zset:leaderboard", "Bob", 87.0);
        ops.add("zset:leaderboard", "Charlie", 92.3);
        ops.add("zset:leaderboard", "David", 78.9);
        ops.add("zset:leaderboard", "Eve", 99.1);

        // ZSCORE: 获取分数
        Double score = ops.score("zset:leaderboard", "Alice");
        log.info("[ZSCORE] Alice 的分数: {}", score);

        // ZRANK: 升序排名（从 0 开始）
        Long rank = ops.rank("zset:leaderboard", "Alice");
        log.info("[ZRANK] Alice 升序排名: {}", rank);

        // ZREVRANK: 降序排名
        Long revRank = ops.reverseRank("zset:leaderboard", "Alice");
        log.info("[ZREVRANK] Alice 降序排名: {}", revRank);

        // ZCARD: 总成员数
        Long card = ops.zCard("zset:leaderboard");
        log.info("[ZCARD] 总人数: {}", card);

        // ZINCRBY: 分数自增
        Double newScore = ops.incrementScore("zset:leaderboard", "Alice", 5.0);
        log.info("[ZINCRBY] Alice 加 5 分后: {}", newScore);

        redisTemplate.delete("zset:leaderboard");
        return "score=" + score + ", rank=" + revRank;
    }

    /**
     * ZSet 范围查询 —— 排行榜核心
     * <p>
     * ZRANGE: 按分数升序获取指定排名范围的成员
     * ZREVRANGE: 按分数降序获取（排行榜最常用）
     * ZRANGEBYSCORE: 按分数范围获取（筛选特定分数区间）
     * ZCOUNT: 统计分数范围内的成员数量
     * <p>
     * WITHSCORES: 同时返回分数
     */
    public String rangeQuery() {
        var ops = redisTemplate.opsForZSet();

        // 准备数据
        ops.add("zset:rank", "Alice", 95.5);
        ops.add("zset:rank", "Bob", 87.0);
        ops.add("zset:rank", "Charlie", 92.3);
        ops.add("zset:rank", "David", 78.9);
        ops.add("zset:rank", "Eve", 99.1);

        // ZREVRANGE: 降序 Top 3（排行榜最常用）
        Set<ZSetOperations.TypedTuple<String>> top3 = ops.reverseRangeWithScores("zset:rank", 0, 2);
        log.info("[ZREVRANGE] Top 3 排行榜:");
        int rank = 1;
        for (var tuple : top3) {
            log.info("  第{}名: {} ({}分)", rank++, tuple.getValue(), tuple.getScore());
        }

        // ZRANGEBYSCORE: 按分数范围查询
        // 分数在 80~95 之间的成员
        Set<String> range80to95 = ops.rangeByScore("zset:rank", 80, 95);
        log.info("[ZRANGEBYSCORE 80~95] {}", range80to95);

        // ZCOUNT: 统计分数范围内的数量
        Long count = ops.count("zset:rank", 80.0, 100.0);
        log.info("[ZCOUNT 80~100] 人数: {}", count);

        // ZREVRANGEBYSCORE: 降序按分数范围
        Set<String> revRange = ops.reverseRangeByScore("zset:rank", 90, 100);
        log.info("[ZREVRANGEBYSCORE 90~100] {}", revRange);

        redisTemplate.delete("zset:rank");
        return "top3=" + top3;
    }

    /**
     * ZSet 应用：延迟队列
     * <p>
     * 思路：
     * 1. 生产者 ZADD task_queue timestamp task_id（分数为执行时间戳）
     * 2. 消费者轮询 ZRANGEBYSCORE task_queue 0 current_timestamp
     * 3. 取出任务后 ZREM 删除
     * 4. 如果处理失败，重新 ZADD 回去
     * <p>
     * 优势：
     * - 天然按时间排序
     * - 支持延迟执行
     * - 支持任务重试
     */
    public String delayQueue() {
        var ops = redisTemplate.opsForZSet();

        long now = System.currentTimeMillis();

        // 生产者：添加延迟任务（分数为执行时间戳）
        ops.add("zset:delay_queue", "task:email:1001", now + 5000);  // 5秒后执行
        ops.add("zset:delay_queue", "task:sms:1002", now + 10000);   // 10秒后执行
        ops.add("zset:delay_queue", "task:push:1003", now - 1000);   // 已到期

        // 消费者：获取到期任务
        Set<String> readyTasks = ops.rangeByScore("zset:delay_queue", 0, now);
        log.info("[延迟队列] 到期任务: {}", readyTasks);

        // 处理并删除
        for (String task : readyTasks) {
            ops.remove("zset:delay_queue", task);
            log.info("[延迟队列] 处理任务: {}", task);
        }

        // 查看剩余任务
        Long remaining = ops.zCard("zset:delay_queue");
        log.info("[延迟队列] 剩余待处理: {}", remaining);

        redisTemplate.delete("zset:delay_queue");
        return "到期任务=" + readyTasks;
    }

    /**
     * ZSet 应用：滑动窗口限流
     * <p>
     * 思路：
     * 1. 每个请求 ZADD rate:{user_id} timestamp request_id
     * 2. 删除窗口外的旧记录 ZREMRANGEBYSCORE rate:{user_id} 0 (now - window)
     * 3. 统计窗口内请求数 ZCARD rate:{user_id}
     * 4. 若超过阈值则拒绝
     * <p>
     * 优点：精确的滑动窗口，无边界问题
     * 缺点：内存消耗较大（每请求一条记录）
     */
    public String slidingWindowRateLimit() {
        var ops = redisTemplate.opsForZSet();

        String rateLimitKey = "zset:rate:user:1001";
        long windowMs = 10000; // 10 秒窗口
        int maxRequests = 5;   // 最多 5 次请求

        // 模拟 7 次请求
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            // 清除窗口外的记录
            ops.removeRangeByScore(rateLimitKey, 0, now - windowMs);

            // 统计当前窗口内请求数
            Long currentCount = ops.zCard(rateLimitKey);

            if (currentCount != null && currentCount >= maxRequests) {
                log.info("[限流] 请求{}被拒绝 (当前窗口请求数={})", i + 1, currentCount);
            } else {
                // 记录本次请求
                ops.add(rateLimitKey, "req:" + i, now + i * 100);
                log.info("[限流] 请求{}通过 (当前窗口请求数={})", i + 1, currentCount + 1);
            }
        }

        redisTemplate.delete(rateLimitKey);
        return "限流演示完成";
    }
}
