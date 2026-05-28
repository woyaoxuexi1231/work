package com.example.redis.c04_typecmds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 4. 各类型核心命令 —— List
 * <p>
 * List 是有序的字符串列表，底层使用 quicklist（ziplist + 双向链表的混合体）。
 * <p>
 * 特性：
 * - 有序：按插入顺序排列
 * - 可重复：允许相同元素
 * - 双端操作：左右两端均可 O(1) 插入弹出
 * - 阻塞弹出：BLPOP/BRPOP 无元素时阻塞等待
 * <p>
 * 应用场景：
 * - 消息队列：LPUSH + BRPOP 实现简单 FIFO
 * - 最新列表：LPUSH + LTRIM 保留最新 N 条
 * - 时间线：LPUSH 最新消息，LRANGE 分页获取
 * - 任务队列：生产者 LPUSH，消费者 RPOP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListCmdDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * List 基础操作
     * <p>
     * LPUSH: 左端插入（头部）
     * RPUSH: 右端插入（尾部）
     * LPOP: 左端弹出
     * RPOP: 右端弹出
     * LRANGE: 范围查询（0 到 -1 表示全部）
     * LINDEX: 按索引获取
     * LLEN: 获取长度
     */
    public String basicOps() {
        ListOperations<String, String> ops = redisTemplate.opsForList();

        // 清空旧数据
        redisTemplate.delete("list:queue");

        // RPUSH: 从右侧依次插入 → [a, b, c, d, e]
        ops.rightPush("list:queue", "a");
        ops.rightPush("list:queue", "b");
        ops.rightPush("list:queue", "c");

        // LPUSH: 从左侧插入 → [z, y, a, b, c]
        ops.leftPush("list:queue", "y");
        ops.leftPush("list:queue", "z");

        // LRANGE: 获取指定范围（闭区间，0 到 -1 表示全部）
        List<String> all = ops.range("list:queue", 0, -1);
        log.info("[LRANGE 0 -1] 全部元素: {}", all); // [z, y, a, b, c]

        // LINDEX: 按索引获取
        String first = ops.index("list:queue", 0);
        String last = ops.index("list:queue", -1);
        log.info("[LINDEX] 第一个={}, 最后一个={}", first, last);

        // LLEN: 获取长度
        Long size = ops.size("list:queue");
        log.info("[LLEN] 长度={}", size);

        // LPOP / RPOP: 弹出
        String leftPop = ops.leftPop("list:queue");
        String rightPop = ops.rightPop("list:queue");
        log.info("[LPOP] {}, [RPOP] {}", leftPop, rightPop);

        // LRANGE 弹出后
        List<String> remaining = ops.range("list:queue", 0, -1);
        log.info("[弹出后] 剩余: {}", remaining);

        redisTemplate.delete("list:queue");
        return "all=" + all + ", leftPop=" + leftPop + ", rightPop=" + rightPop;
    }

    /**
     * 阻塞弹出 —— 简单消息队列的核心
     * <p>
     * BLPOP key [key ...] timeout
     * BRPOP key [key ...] timeout
     * <p>
     * 阻塞行为：
     * - 列表非空：立即返回
     * - 列表为空：阻塞等待，直到有新元素或超时
     * - timeout=0：永久阻塞（慎用）
     * <p>
     * 多键阻塞：按顺序检查多个列表，返回第一个非空的
     * 这实现了简单的优先级队列
     */
    public String blockingPop() {
        ListOperations<String, String> ops = redisTemplate.opsForList();

        // 先 push 一条消息
        ops.rightPush("list:mq", "message-1");
        ops.rightPush("list:mq", "message-2");

        // BRPOP: 阻塞弹出（超时 3 秒）
        // 注意：Spring Data Redis 的 rightPop(key, timeout, unit) 就是 BRPOP
        String result = ops.rightPop("list:mq", 3, TimeUnit.SECONDS);
        log.info("[BRPOP] 消费消息: {}", result);

        result = ops.rightPop("list:mq", 3, TimeUnit.SECONDS);
        log.info("[BRPOP] 消费消息: {}", result);

        // 此时队列为空，再次 BRPOP 会阻塞直到超时
        log.info("[BRPOP] 队列为空，等待 3 秒超时...");
        result = ops.rightPop("list:mq", 3, TimeUnit.SECONDS);
        log.info("[BRPOP] 超时返回: {}", result); // null

        redisTemplate.delete("list:mq");
        return "消费完成";
    }

    /**
     * List 高级操作
     * <p>
     * LINSERT: 在指定元素前/后插入
     * LREM: 删除指定数量的匹配元素
     * LSET: 按索引设置值
     * LTRIM: 裁剪列表，只保留指定范围
     * <p>
     * 应用：保留最新 N 条记录
     * LPUSH + LTRIM 组合 → 新消息插入头部，超出 N 条的旧消息自动丢弃
     */
    public String advancedOps() {
        ListOperations<String, String> ops = redisTemplate.opsForList();

        // LINSERT: 在元素前/后插入
        ops.rightPushAll("list:demo", Arrays.asList("a", "b", "c", "d"));
        ops.leftPush("list:demo", "b", "x"); // 在 b 前面插入 x
        List<String> afterInsert = ops.range("list:demo", 0, -1);
        log.info("[LINSERT] 在b前插入x: {}", afterInsert); // [a, x, b, c, d]

        // LREM: 删除匹配元素
        // LREM key count value
        // count > 0: 从头到尾删除 count 个
        // count < 0: 从尾到头删除 |count| 个
        // count = 0: 删除所有匹配的
        ops.rightPushAll("list:rem", Arrays.asList("a", "b", "a", "c", "a"));
        ops.remove("list:rem", 2, "a"); // 从头删除 2 个 "a"
        List<String> afterRem = ops.range("list:rem", 0, -1);
        log.info("[LREM] 删除2个a后: {}", afterRem); // [c, a]

        // LSET: 按索引修改
        ops.set("list:demo", 0, "A");
        List<String> afterSet = ops.range("list:demo", 0, -1);
        log.info("[LSET] index=0 设为A: {}", afterSet);

        // LTRIM: 裁剪列表（保留最新 N 条的经典模式）
        redisTemplate.delete("list:recent");
        for (int i = 1; i <= 10; i++) {
            ops.leftPush("list:recent", "item-" + i);
            ops.trim("list:recent", 0, 4); // 只保留最新 5 条
        }
        List<String> recent = ops.range("list:recent", 0, -1);
        log.info("[LTRIM] 保留最新5条: {}", recent);

        redisTemplate.delete(Arrays.asList("list:demo", "list:rem", "list:recent"));
        return "afterInsert=" + afterInsert + ", recent=" + recent;
    }
}
