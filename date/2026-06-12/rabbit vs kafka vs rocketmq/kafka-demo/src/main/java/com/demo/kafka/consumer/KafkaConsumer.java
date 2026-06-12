package com.demo.kafka.consumer;

import com.demo.kafka.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka 消费者
 * 使用手动 ACK 模式（manual_immediate），演示可靠性消费
 */
@Slf4j
@Component
public class KafkaConsumer {

    // ==================== 基础消费 ====================

    @KafkaListener(topics = KafkaConfig.BASIC_TOPIC, groupId = "demo-basic-group")
    public void consumeBasic(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("✅ [Basic] 收到消息: partition={}, offset={}, key={}, value={}",
                record.partition(), record.offset(), record.key(), record.value());
        // 手动提交偏移量
        ack.acknowledge();
    }

    // ==================== Key 路由消费 ====================

    @KafkaListener(topics = KafkaConfig.KEY_ROUTE_TOPIC, groupId = "demo-key-group")
    public void consumeKeyRoute(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("✅ [KeyRoute] 收到消息: partition={}, key={}, value={}",
                record.partition(), record.key(), record.value());
        ack.acknowledge();
    }

    // ==================== 性能压测消费 ====================

    @KafkaListener(topics = KafkaConfig.BENCH_TOPIC, groupId = "demo-bench-group", concurrency = "6")
    public void consumeBench(ConsumerRecord<String, String> record, Acknowledgment ack) {
        // 压测场景：快速消费，不打印日志
        ack.acknowledge();
    }

    // ==================== 可靠性消费 ====================

    @KafkaListener(topics = KafkaConfig.RELIABLE_TOPIC, groupId = "demo-reliable-group")
    public void consumeReliable(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            log.info("✅ [Reliable] 处理消息: partition={}, offset={}, key={}, value={}",
                    record.partition(), record.offset(), record.key(), record.value());
            // 处理成功，手动提交
            ack.acknowledge();
            log.info("✅ [Reliable] 偏移量已提交");
        } catch (Exception e) {
            log.error("🚫 [Reliable] 消息处理失败: {}", e.getMessage());
            // 不提交 ACK，消息会被重新消费（取决于 retry 配置）
        }
    }

    // ==================== 顺序消息消费 ====================

    /**
     * 顺序消息消费 - 单线程消费同一分区
     * concurrency=1 保证同一分区只有一个线程消费，从而保证顺序
     */
    @KafkaListener(topics = KafkaConfig.ORDER_TOPIC, groupId = "demo-order-group", concurrency = "1")
    public void consumeOrder(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("✅ [Order] 收到顺序消息: partition={}, offset={}, key={}, value={}",
                record.partition(), record.offset(), record.key(), record.value());
        ack.acknowledge();
    }

    // ==================== 事务消息消费 ====================

    /**
     * 事务消息消费 - 需要配置 isolation.level=read_committed
     * 只能消费到已提交的事务消息，未提交/回滚的消息不可见
     */
    @KafkaListener(topics = KafkaConfig.TRANSACTION_TOPIC, groupId = "demo-tx-group",
            properties = {"isolation.level=read_committed"})
    public void consumeTransaction(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("✅ [TX] 收到事务消息: partition={}, offset={}, value={}",
                record.partition(), record.offset(), record.value());
        ack.acknowledge();
    }

    // ==================== 压缩消息消费 ====================

    @KafkaListener(topics = KafkaConfig.COMPRESS_TOPIC, groupId = "demo-compress-group")
    public void consumeCompress(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("✅ [Compress] 收到消息: partition={}, offset={}", record.partition(), record.offset());
        ack.acknowledge();
    }
}
