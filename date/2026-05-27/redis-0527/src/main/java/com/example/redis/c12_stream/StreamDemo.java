package com.example.redis.c12_stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 12. Stream 消息队列
 * <p>
 * Stream 是 Redis 5.0 引入的消息队列数据结构。
 * 相比 Pub/Sub，Stream 提供了持久化、消费者组、ACK 确认等能力。
 * <p>
 * 核心特性：
 * - 消息持久化：消息存储在 Redis 中，重启后不丢失
 * - 消费者组：组内消息只被消费一次（类似 Kafka 的 Consumer Group）
 * - ACK 确认：消费者处理完消息后确认，未确认可重投递
 * - 死信：长时间未确认的消息可由其他消费者认领（XCLAIM）
 * - 消息裁剪：XTRIM 控制流大小
 * <p>
 * Stream vs 消息队列中间件：
 * - 轻量级：无需额外部署 MQ 中间件
 * - 功能有限：不支持复杂的路由规则
 * - 性能：单实例约 10 万 QPS
 * - 适合：简单的消息队列场景
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * Stream 完整流程演示
     * <p>
     * 生产者 → 消费者组 → 消费者 → ACK 确认
     */
    public String fullFlow() {
        redisTemplate.delete("stream:orders");

        StreamOperations<String, String, String> ops = redisTemplate.opsForStream();

        // 1. 创建消费者组
        try {
            ops.createGroup("stream:orders", "order-group");
        } catch (Exception ignored) {
        }

        // 2. 生产者添加消息
        for (int i = 1; i <= 5; i++) {
            Map<String, String> orderFields = new HashMap<>();
            orderFields.put("order_id", "ORD-" + i);
            orderFields.put("user_id", "USER-" + (i % 3 + 1));
            orderFields.put("amount", String.valueOf(i * 100.0));
            ops.add("stream:orders", orderFields);
        }
        log.info("[Stream] 生产 5 条订单消息");

        // 3. 消费者读取消息
        List<MapRecord<String, String, String>> records = ops.read(
                Consumer.from("order-group", "worker-1"),
                StreamReadOptions.empty().count(3),
                StreamOffset.create("stream:orders", ReadOffset.lastConsumed())
        );

        log.info("[Stream] worker-1 读取 {} 条消息:", records.size());
        for (MapRecord<String, String, String> record : records) {
            log.info("  订单: {}", record.getValue());

            // 4. 处理完成后 ACK
            ops.acknowledge("stream:orders", "order-group", record.getId());
        }

        // 5. 查看未确认消息（应为 0，因为全部 ACK 了）
        PendingMessagesSummary pendingSummary = ops.pending("stream:orders", "order-group");
        long pendingCount = pendingSummary != null ? pendingSummary.getTotalPendingMessages() : 0;
        log.info("[Stream] 未确认消息: {}", pendingCount);

        // 6. 流信息
        StreamInfo.XInfoStream info = ops.info("stream:orders");
        log.info("[Stream] 流长度: {}", info.streamLength());

        redisTemplate.delete("stream:orders");
        return "消费 " + records.size() + " 条, 未确认 " + pendingCount;
    }

    /**
     * 未确认消息重投递（XCLAIM）
     * <p>
     * 场景：消费者处理消息时崩溃，消息一直处于未确认状态。
     * 其他消费者可以通过 XCLAIM 认领这些超时消息。
     */
    public String pendingRedelivery() {
        redisTemplate.delete("stream:pending");

        StreamOperations<String, String, String> ops2 = redisTemplate.opsForStream();

        try {
            ops2.createGroup("stream:pending", "my-group");
        } catch (Exception ignored) {
        }

        // 添加消息
        Map<String, String> fields1 = new HashMap<>();
        fields1.put("task", "process-payment");
        ops2.add("stream:pending", fields1);
        Map<String, String> fields2 = new HashMap<>();
        fields2.put("task", "send-email");
        ops2.add("stream:pending", fields2);

        // 消费者读取但不 ACK（模拟崩溃）
        ops2.read(
                Consumer.from("my-group", "crashed-worker"),
                StreamReadOptions.empty().count(2),
                StreamOffset.create("stream:pending", ReadOffset.lastConsumed())
        );

        // 查看未确认消息
        PendingMessagesSummary pendingSummary2 = ops2.pending("stream:pending", "my-group");
        long pendingCount2 = pendingSummary2 != null ? pendingSummary2.getTotalPendingMessages() : 0;
        log.info("[重投递] 未确认消息数: {}", pendingCount2);

        // 注意：XCLAIM 需要知道具体的消息 ID
        // 在实际场景中，可以通过 PendingMessages 获取未确认消息列表
        // 然后对超时的消息进行认领
        log.info("[重投递] 新消费者可以通过 XCLAIM 认领超时的未确认消息");

        redisTemplate.delete("stream:pending");
        return "重投递演示完成";
    }

    /**
     * Stream 消息裁剪
     * <p>
     * 控制 Stream 的内存占用：
     * - XTRIM stream MAXLEN ~ N: 近似裁剪到 N 条
     * - XTRIM stream MINID ~ timestamp: 删除指定 ID 之前的消息
     */
    public String streamTrim() {
        redisTemplate.delete("stream:trim");

        StreamOperations<String, String, String> ops3 = redisTemplate.opsForStream();

        // 添加消息
        for (int i = 0; i < 100; i++) {
            Map<String, String> trimFields = new HashMap<>();
            trimFields.put("seq", String.valueOf(i));
            ops3.add("stream:trim", trimFields);
        }

        Long before = ops3.size("stream:trim");
        log.info("[裁剪] 裁剪前: {} 条", before);

        // 裁剪到最新 10 条
        ops3.trim("stream:trim", 10, true);

        Long after = ops3.size("stream:trim");
        log.info("[裁剪] 裁剪后: {} 条", after);

        redisTemplate.delete("stream:trim");
        return "裁剪: " + before + " → " + after;
    }
}
