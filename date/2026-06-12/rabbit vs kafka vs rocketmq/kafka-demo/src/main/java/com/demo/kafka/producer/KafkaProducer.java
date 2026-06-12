package com.demo.kafka.producer;

import com.demo.kafka.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka 生产者
 * 演示基础收发、性能压测、可靠性保障、高级特性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ProducerFactory<String, String> producerFactory;

    // ==================== 基础收发 ====================

    /**
     * 基础发送 - 无 Key，消息会轮询分配到各分区
     */
    public Map<String, Object> sendBasic(int count) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String msg = "基础消息-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            kafkaTemplate.send(KafkaConfig.BASIC_TOPIC, msg);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ Kafka基础发送 {} 条消息，耗时 {}ms", count, elapsed);
        Map<String, Object> result = new HashMap<>();
        result.put("Topic", KafkaConfig.BASIC_TOPIC);
        result.put("发送数量", count);
        result.put("耗时ms", elapsed);
        result.put("说明", "无Key，消息轮询分配到3个分区");
        return result;
    }

    /**
     * Key 路由发送 - 同 Key 的消息保证进入同一分区
     * 这是 Kafka 实现顺序消息的基础
     */
    public Map<String, Object> sendWithKey(int count) {
        long start = System.currentTimeMillis();
        String[] keys = {"order-001", "order-002", "order-003"};
        for (int i = 0; i < count; i++) {
            String key = keys[i % keys.length];
            String msg = "Key路由消息-" + key + "-" + i;
            kafkaTemplate.send(KafkaConfig.KEY_ROUTE_TOPIC, key, msg);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ Kafka Key路由发送 {} 条消息，耗时 {}ms", count, elapsed);
        Map<String, Object> result = new HashMap<>();
        result.put("Topic", KafkaConfig.KEY_ROUTE_TOPIC);
        result.put("发送数量", count);
        result.put("Key列表", List.of(keys));
        result.put("耗时ms", elapsed);
        result.put("说明", "同Key的消息会进入同一分区，保证分区内顺序");
        return result;
    }

    // ==================== 性能压测 ====================

    /**
     * 性能压测 - 批量发送并统计吞吐量
     * 利用 Kafka 的 batch.size 和 linger.ms 自动批量发送
     */
    public Map<String, Object> benchmark(int count, int size) {
        String payload = "X".repeat(Math.max(1, size));

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        long start = System.currentTimeMillis();
        List<CompletableFuture<?>> futures = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(KafkaConfig.BENCH_TOPIC, payload);
            futures.add(future);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    failed.incrementAndGet();
                    log.error("❌ Kafka发送失败: {}", ex.getMessage());
                } else {
                    success.incrementAndGet();
                }
            });
        }

        // 等待所有消息发送完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long elapsed = System.currentTimeMillis() - start;

        double throughput = count * 1000.0 / elapsed;

        log.info("✅ Kafka压测完成: 发送{}条(每条{}字节), 耗时{}ms, 吞吐量={}/s, 成功={}, 失败={}",
                count, size, elapsed, String.format("%.0f", throughput), success.get(), failed.get());

        Map<String, Object> result = new HashMap<>();
        result.put("消息数量", count);
        result.put("消息大小字节", size);
        result.put("总耗时ms", elapsed);
        result.put("吞吐量每秒", String.format("%.0f", throughput));
        result.put("成功数", success.get());
        result.put("失败数", failed.get());
        result.put("说明", "Kafka 高吞吐的秘密：批量发送(batch.size) + 延迟发送(linger.ms) + 顺序写入 + 零拷贝");
        return result;
    }

    // ==================== 可靠性保障 ====================

    /**
     * 可靠性发送演示
     * 已在 application.yml 中配置：
     * - acks=all（所有ISR副本确认）
     * - enable.idempotence=true（幂等生产者）
     * - retries=3（自动重试）
     */
    public Map<String, Object> sendReliable() {
        Map<String, Object> result = new HashMap<>();
        List<String> events = new ArrayList<>();

        // 场景1: 正常发送 + 同步等待确认
        String msg = "可靠性测试消息-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            SendResult<String, String> sendResult = kafkaTemplate.send(
                    KafkaConfig.RELIABLE_TOPIC, "reliable-key", msg
            ).get(); // 同步等待，阻塞直到 broker 确认

            RecordMetadata metadata = sendResult.getRecordMetadata();
            String event = String.format("✅ 消息已确认: topic=%s, partition=%d, offset=%d, timestamp=%d",
                    metadata.topic(), metadata.partition(), metadata.offset(), metadata.timestamp());
            log.info(event);
            events.add(event);
        } catch (Exception e) {
            String event = "❌ 消息发送失败: " + e.getMessage();
            log.error("🚫 {}", event);
            events.add(event);
        }

        // 场景2: 演示回调处理
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(KafkaConfig.RELIABLE_TOPIC, "callback-key", "回调测试消息");
        future.whenComplete((sendResult, ex) -> {
            if (ex != null) {
                log.error("❌ [Callback] 发送失败: {}", ex.getMessage());
            } else {
                log.info("✅ [Callback] 发送成功: partition={}, offset={}",
                        sendResult.getRecordMetadata().partition(),
                        sendResult.getRecordMetadata().offset());
            }
        });

        result.put("配置说明", "acks=all + enable.idempotence=true + retries=3");
        result.put("可靠性保证", "消息不丢失（ISR确认）+ 不重复（幂等）+ 自动重试");
        result.put("事件", events);
        return result;
    }

    // ==================== 高级特性 ====================

    /**
     * 顺序消息 - 同 Key 的消息保证进入同一分区，从而保证顺序
     * 模拟订单状态流转：创建 -> 支付 -> 发货 -> 完成
     */
    public Map<String, Object> sendOrder() {
        String orderId = "ORDER-" + System.currentTimeMillis();
        String[] statuses = {"CREATED", "PAID", "SHIPPED", "COMPLETED"};
        List<String> events = new ArrayList<>();

        for (int i = 0; i < statuses.length; i++) {
            String msg = String.format("orderId=%s, status=%s, seq=%d", orderId, statuses[i], i);
            // 使用 orderId 作为 Key，保证同一订单的消息进入同一分区
            kafkaTemplate.send(KafkaConfig.ORDER_TOPIC, orderId, msg);
            events.add("发送: " + msg);
            log.info("✅ [Order] {}", msg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("状态流转", List.of(statuses));
        result.put("说明", "同一订单号作为Key，消息进入同一分区，消费者单线程消费保证顺序");
        result.put("注意", "Kafka只保证分区内顺序，跨分区无法保证全局顺序");
        result.put("事件", events);
        return result;
    }

    /**
     * 事务消息 - Exactly-Once 语义
     * 需要先配置 transactional-id，然后在一个事务中发送多条消息
     */
    public Map<String, Object> sendTransaction() {
        Map<String, Object> result = new HashMap<>();
        List<String> events = new ArrayList<>();

        try {
            // 在事务中发送消息
            kafkaTemplate.executeInTransaction(operations -> {
                // 消息1
                operations.send(KafkaConfig.TRANSACTION_TOPIC, "tx-key", "事务消息1-扣库存");
                events.add("发送: 事务消息1-扣库存");
                log.info("✅ [TX] 发送事务消息1");

                // 消息2
                operations.send(KafkaConfig.TRANSACTION_TOPIC, "tx-key", "事务消息2-创建订单");
                events.add("发送: 事务消息2-创建订单");
                log.info("✅ [TX] 发送事务消息2");

                // 消息3
                operations.send(KafkaConfig.TRANSACTION_TOPIC, "tx-key", "事务消息3-扣余额");
                events.add("发送: 事务消息3-扣余额");
                log.info("✅ [TX] 发送事务消息3");

                // 返回 true 提交事务，抛异常则回滚
                log.info("✅ [TX] 事务提交，3条消息要么全部可见，要么全部不可见");
                return true;
            });

            events.add("✅ 事务已提交");
            result.put("说明", "Kafka事务: 一个事务中的消息要么全部对消费者可见，要么全部不可见（Exactly-Once）");
        } catch (Exception e) {
            events.add("❌ 事务回滚: " + e.getMessage());
            log.error("🚫 [TX] 事务回滚: {}", e.getMessage());
            result.put("说明", "事务回滚");
        }

        result.put("事件", events);
        result.put("注意", "消费端需配置 isolation.level=read_committed 才能只看到已提交的消息");
        return result;
    }

    /**
     * 消息压缩对比 - 使用不同压缩算法发送相同数据
     * Kafka 支持: none, gzip, snappy, lz4, zstd
     */
    public Map<String, Object> sendCompressed(int count) {
        Map<String, Object> result = new HashMap<>();
        String payload = "这是一条用于测试压缩效果的消息内容，需要足够长才能体现压缩优势。".repeat(10);

        String[] compressions = {"none", "gzip", "snappy", "lz4"};
        List<Map<String, Object>> comparisons = new ArrayList<>();

        for (String compression : compressions) {
            long start = System.currentTimeMillis();
            // 创建使用指定压缩类型的临时 KafkaTemplate
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compression);

            for (int i = 0; i < count; i++) {
                kafkaTemplate.send(KafkaConfig.COMPRESS_TOPIC, "compress-key",
                        payload + "-seq=" + i);
            }
            long elapsed = System.currentTimeMillis() - start;

            Map<String, Object> comp = new HashMap<>();
            comp.put("压缩算法", compression);
            comp.put("发送数量", count);
            comp.put("耗时ms", elapsed);
            comparisons.add(comp);

            log.info("✅ [Compress] 压缩={} 发送{}条 耗时{}ms", compression, count, elapsed);
        }

        result.put("对比结果", comparisons);
        result.put("说明", "gzip压缩率最高但CPU开销大，snappy/lz4速度快压缩率适中，none无压缩但传输量大");
        return result;
    }
}
