package com.example.redis.c04_typecmds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 4. 各类型核心命令 —— Stream
 * <p>
 * Stream 是 Redis 5.0 引入的消息队列数据类型，专为消息传递设计。
 * 底层使用 radix tree + listpack 编码。
 * <p>
 * 核心概念：
 * - 消息 ID: 时间戳-序号（如 1638307200000-0），自动生成或手动指定
 * - 消费者组: 多个消费者组成一组，消息在组内只被消费一次
 * - ACK 确认: 消费者处理完消息后发送 ACK，未确认的消息可重投递
 * <p>
 * 对比 Pub/Sub：
 * - Pub/Sub: 无持久化，消息发完即丢，无消费者组
 * - Stream: 持久化存储，支持消费者组、ACK、重投递，类似 Kafka
 * <p>
 * 核心命令：
 * - XADD: 添加消息
 * - XREAD / XREADGROUP: 读取消息
 * - XACK: 确认消息
 * - XGROUP: 管理消费者组
 * - XPENDING: 查看未确认消息
 * - XCLAIM: 认领未确认消息
 * - XTRIM: 裁剪消息流
 * - XLEN / XRANGE / XREVRANGE: 查询消息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamCmdDemo {

    private final StringRedisTemplate redisTemplate;
    private final RedisConnectionFactory connectionFactory;

    /**
     * Stream 基础操作
     * <p>
     * XADD: 添加消息到流
     *   - ID 自动生成: XADD stream * field value
     *   - 手动指定: XADD stream 1638307200000-0 field value
     * <p>
     * XLEN: 流中的消息数量
     * XRANGE: 按 ID 范围查询（正序）
     * XREVRANGE: 按 ID 范围查询（倒序）
     */
    public String basicOps() {
        redisTemplate.delete("stream:orders");

        var ops = redisTemplate.opsForStream();

        // XADD: 添加消息（ID 自动生成）
        String id1 = ops.add("stream:orders",
                Map.of("user", "1001", "action", "create", "amount", "99.9"));
        String id2 = ops.add("stream:orders",
                Map.of("user", "1002", "action", "pay", "amount", "199.0"));
        String id3 = ops.add("stream:orders",
                Map.of("user", "1001", "action", "cancel", "amount", "99.9"));

        log.info("[XADD] 消息ID: {}, {}, {}", id1, id2, id3);

        // XLEN: 消息数量
        Long len = ops.size("stream:orders");
        log.info("[XLEN] 消息数量: {}", len);

        // XRANGE: 获取所有消息
        List<MapRecord<String, Object, Object>> messages = ops.range("stream:orders",
                Range.unbounded());
        for (var msg : messages) {
            log.info("[XRANGE] ID={}, fields={}", msg.getId(), msg.getValue());
        }

        redisTemplate.delete("stream:orders");
        return "消息数=" + len + ", 第一条=" + id1;
    }

    /**
     * Stream 消费者组 —— 消息队列核心
     * <p>
     * 消费者组保证：
     * - 组内每条消息只被消费一次
     * - 支持消息确认（ACK）
     * - 未确认消息可重投递给其他消费者
     * <p>
     * XREADGROUP: 从消费者组读取消息
     * XACK: 确认消息已处理
     * XPENDING: 查看未确认消息
     * XCLAIM: 认领超时未确认的消息
     */
    public String consumerGroup() {
        redisTemplate.delete("stream:events");

        var ops = redisTemplate.opsForStream();

        // 添加测试消息
        for (int i = 0; i < 5; i++) {
            ops.add("stream:events",
                    Map.of("event", "click", "page", "/home", "seq", String.valueOf(i)));
        }

        // 创建消费者组
        try {
            ops.createGroup("stream:events", "order-process-group");
            log.info("[XGROUP] 创建消费者组: order-process-group");
        } catch (Exception e) {
            // 组已存在则忽略
            log.info("[XGROUP] 消费者组已存在");
        }

        // XREADGROUP: 消费者读取消息
        // consumer-1 从组中读取 2 条消息
        List<MapRecord<String, Object, Object>> records = ops.read(Consumer.from("order-process-group", "consumer-1"),
                StreamReadOptions.empty().count(2).noAck(),
                StreamOffset.create("stream:events", ReadOffset.lastConsumed()));

        log.info("[XREADGROUP] consumer-1 读取 {} 条消息:", records.size());
        for (var record : records) {
            log.info("  ID={}, data={}", record.getId(), record.getValue());

            // XACK: 确认消息
            ops.acknowledge("stream:events", "order-process-group", record.getId());
            log.info("[XACK] 确认消息: {}", record.getId());
        }

        // XPENDING: 查看未确认消息
        PendingMessages pending = ops.pending("stream:events", "order-process-group",
                Range.unbounded(), 10);
        log.info("[XPENDING] 未确认消息数: {}", pending.getTotal());

        redisTemplate.delete("stream:events");
        return "消费完成, 读取=" + records.size();
    }

    /**
     * Stream 消息裁剪
     * <p>
     * XTRIM: 控制流的长度
     * - XTRIM stream MAXLEN ~ 1000: 近似裁剪到 1000 条（高效）
     * - XTRIM stream MAXLEN 1000: 精确裁剪到 1000 条
     * <p>
     * XADD 时也可以指定 MAXLEN:
     * XADD stream MAXLEN ~ 1000 * field value
     * <p>
     * ~ 表示近似裁剪，Redis 会实际裁剪到略大于目标值的 2 的幂
     * 近似裁剪性能更好，适合大多数场景
     */
    public String streamTrim() {
        redisTemplate.delete("stream:trim");

        var ops = redisTemplate.opsForStream();

        // 添加 100 条消息
        for (int i = 0; i < 100; i++) {
            ops.add("stream:trim", Map.of("seq", String.valueOf(i)));
        }
        log.info("[XADD] 添加 100 条消息, XLEN={}", ops.size("stream:trim"));

        // XTRIM: 裁剪到最新 10 条
        Long trimmed = ops.trim("stream:trim", 10, true);
        log.info("[XTRIM] 裁剪后 XLEN={}, 删除了 {} 条", ops.size("stream:trim"), trimmed);

        redisTemplate.delete("stream:trim");
        return "裁剪完成";
    }
}
