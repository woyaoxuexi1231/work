package com.demo.rocketmq.producer;

import com.demo.rocketmq.config.RocketmqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RocketMQ 生产者
 * 演示基础收发、性能压测、可靠性保障、高级特性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RocketmqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    // ==================== 基础收发 ====================

    /**
     * 同步发送 - 发送后阻塞等待 Broker 确认
     * 可靠性最高，适合重要消息
     */
    public Map<String, Object> sendSync(int count) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String msg = "同步消息-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            rocketMQTemplate.convertAndSend(RocketmqConfig.BASIC_TOPIC, msg);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ RocketMQ同步发送 {} 条消息，耗时 {}ms", count, elapsed);
        Map<String, Object> result = new HashMap<>();
        result.put("发送模式", "同步发送（syncSend）");
        result.put("发送数量", count);
        result.put("耗时ms", elapsed);
        return result;
    }

    /**
     * 异步发送 - 发送后立即返回，通过回调获取结果
     * 适合对响应时间敏感的场景
     */
    public Map<String, Object> sendAsync(int count) {
        long start = System.currentTimeMillis();
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            String msg = "异步消息-" + i;
            rocketMQTemplate.asyncSend(RocketmqConfig.BASIC_TOPIC, msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    success.incrementAndGet();
                    latch.countDown();
                }

                @Override
                public void onException(Throwable e) {
                    failed.incrementAndGet();
                    log.error("❌ 异步发送失败: {}", e.getMessage());
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long elapsed = System.currentTimeMillis() - start;

        log.info("✅ RocketMQ异步发送完成: 成功={}, 失败={}, 耗时={}ms", success.get(), failed.get(), elapsed);
        Map<String, Object> result = new HashMap<>();
        result.put("发送模式", "异步发送（asyncSend）");
        result.put("发送数量", count);
        result.put("成功数", success.get());
        result.put("失败数", failed.get());
        result.put("耗时ms", elapsed);
        return result;
    }

    /**
     * 单向发送 - 只发送不等待任何确认
     * 性能最高但可能丢消息，适合日志等不敏感场景
     */
    public Map<String, Object> sendOneWay(int count) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String msg = "单向消息-" + i;
            rocketMQTemplate.sendOneWay(RocketmqConfig.BASIC_TOPIC, msg);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ RocketMQ单向发送 {} 条消息，耗时 {}ms（最快但可能丢消息）", count, elapsed);
        Map<String, Object> result = new HashMap<>();
        result.put("发送模式", "单向发送（sendOneWay）");
        result.put("发送数量", count);
        result.put("耗时ms", elapsed);
        result.put("说明", "只发不等，性能最高，适合日志收集等允许少量丢失的场景");
        return result;
    }

    // ==================== 性能压测 ====================

    /**
     * 性能压测 - 同步发送 N 条消息，统计吞吐量和延迟
     */
    public Map<String, Object> benchmark(int count, int size) {
        String payload = "X".repeat(Math.max(1, size));

        long start = System.currentTimeMillis();
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            long sendTime = System.nanoTime();
            try {
                SendResult result = rocketMQTemplate.syncSend(RocketmqConfig.BENCH_TOPIC, payload);
                long sendNanos = System.nanoTime() - sendTime;
                latencies.add(sendNanos / 1000); // 微秒
                success.incrementAndGet();
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("❌ 压测发送失败: {}", e.getMessage());
            }
        }
        long elapsed = System.currentTimeMillis() - start;

        // 统计延迟分布
        if (!latencies.isEmpty()) {
            latencies.sort(Long::compareTo);
        }
        double avgLatencyUs = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = latencies.isEmpty() ? 0 : latencies.get(latencies.size() / 2);
        long p99 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99));

        double throughput = success.get() * 1000.0 / Math.max(1, elapsed);

        log.info("✅ RocketMQ压测完成: 发送{}条(每条{}字节), 耗时{}ms, 吞吐量={}/s, P50={}μs, P99={}μs",
                success.get(), size, elapsed, String.format("%.0f", throughput), p50, p99);

        Map<String, Object> result = new HashMap<>();
        result.put("消息数量", count);
        result.put("消息大小字节", size);
        result.put("总耗时ms", elapsed);
        result.put("吞吐量每秒", String.format("%.0f", throughput));
        result.put("平均延迟μs", String.format("%.0f", avgLatencyUs));
        result.put("P50延迟μs", p50);
        result.put("P99延迟μs", p99);
        result.put("成功数", success.get());
        result.put("失败数", failed.get());
        return result;
    }

    // ==================== 可靠性保障 ====================

    /**
     * 可靠性发送 - 同步发送 + 重试 + Broker 确认
     * RocketMQ 默认同步发送就有重试机制
     */
    public Map<String, Object> sendReliable() {
        Map<String, Object> result = new HashMap<>();
        List<String> events = new ArrayList<>();

        String msg = "可靠性测试消息-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            // syncSend 默认有3次重试（配置在 application.yml）
            SendResult sendResult = rocketMQTemplate.syncSend(RocketmqConfig.RELIABLE_TOPIC, msg);

            String event = String.format("✅ 消息发送成功: msgId=%s, status=%s, queue=%s",
                    sendResult.getMsgId(),
                    sendResult.getSendStatus(),
                    sendResult.getMessageQueue());
            log.info(event);
            events.add(event);

            // 验证发送状态
            switch (sendResult.getSendStatus()) {
                case SEND_OK:
                    events.add("✅ SEND_OK: 消息已成功存储到 Broker");
                    break;
                case FLUSH_DISK_TIMEOUT:
                    events.add("⚠️ FLUSH_DISK_TIMEOUT: 刷盘超时，消息在内存中但未落盘");
                    break;
                case FLUSH_SLAVE_TIMEOUT:
                    events.add("⚠️ FLUSH_SLAVE_TIMEOUT: 从节点同步超时");
                    break;
                case SLAVE_NOT_AVAILABLE:
                    events.add("⚠️ SLAVE_NOT_AVAILABLE: 从节点不可用");
                    break;
            }
        } catch (Exception e) {
            String event = "❌ 消息发送失败（重试3次后仍失败）: " + e.getMessage();
            log.error("🚫 {}", event);
            events.add(event);
        }

        result.put("配置说明", "同步发送 + retryTimesWhenSendFailed=3 + retryTimesWhenSendAsyncFailed=3");
        result.put("可靠性保证", "同步发送确认 + 自动重试 + 消息持久化");
        result.put("与Kafka对比", "RocketMQ默认同步发送，Kafka默认异步发送；RocketMQ重试在客户端，Kafka由producer自动处理");
        result.put("事件", events);
        return result;
    }

    // ==================== 高级特性 ====================

    /**
     * 延迟消息 - RocketMQ 内置 18 级延迟
     * 延迟级别: 1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m, 8=4m, 9=5m,
     *          10=6m, 11=7m, 12=8m, 13=9m, 14=10m, 15=20m, 16=30m, 17=1h, 18=2h
     *
     * @param delayLevel 延迟级别（1-18）
     */
    public Map<String, Object> sendDelay(int delayLevel) {
        String msg = "延迟消息-" + UUID.randomUUID().toString().substring(0, 8) + "-level=" + delayLevel;

        // RocketMQ 延迟级别对应的毫秒数
        long[] delayMsArray = {0, 1000, 5000, 10000, 30000, 60000, 120000, 180000, 240000,
                300000, 360000, 420000, 480000, 540000, 600000, 1200000, 1800000, 3600000, 7200000};
        long delayMs = delayLevel >= 1 && delayLevel <= 18 ? delayMsArray[delayLevel] : 0;

        Message<String> message = MessageBuilder.withPayload(msg).build();
        // syncSend(destination, message, timeout, delayLevel)
        SendResult result = rocketMQTemplate.syncSend(RocketmqConfig.DELAY_TOPIC, message, 3000, delayLevel);

        log.info("✅ 延迟消息已发送: level={}, 延迟={}ms, msgId={}", delayLevel, delayMs, result.getMsgId());
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("延迟级别", delayLevel);
        resultMap.put("延迟时间", delayMs + "ms");
        resultMap.put("消息", msg);
        resultMap.put("msgId", result.getMsgId());
        resultMap.put("说明", "RocketMQ 内置18级延迟（1s~2h），比 RabbitMQ 的 TTL+DLX 方案更简单");
        resultMap.put("与RabbitMQ对比", "RabbitMQ 需要 TTL+死信交换机 间接实现，RocketMQ 原生支持");
        return resultMap;
    }

    /**
     * 顺序消息 - 使用 MessageQueueSelector 保证同 Key 的消息进入同一队列
     * 模拟订单状态流转
     */
    public Map<String, Object> sendOrder() {
        String orderId = "ORDER-" + System.currentTimeMillis();
        String[] statuses = {"CREATED", "PAID", "SHIPPED", "COMPLETED"};
        List<String> events = new ArrayList<>();

        for (int i = 0; i < statuses.length; i++) {
            String msg = String.format("orderId=%s, status=%s, seq=%d", orderId, statuses[i], i);

            // 使用 syncSendOrderly 保证同 hashKey 的消息进入同一 MessageQueue
            SendResult result = rocketMQTemplate.syncSendOrderly(
                    RocketmqConfig.ORDER_TOPIC, msg, orderId);

            events.add(String.format("发送: %s -> queue=%d", msg, result.getMessageQueue().getQueueId()));
            log.info("✅ [Order] 发送到队列{}: {}", result.getMessageQueue().getQueueId(), msg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("状态流转", List.of(statuses));
        result.put("说明", "syncSendOrderly: 同 hashKey 的消息通过 hash 取模进入同一 MessageQueue");
        result.put("与Kafka对比", "Kafka 用 Key 路由实现同样效果，原理类似（hash -> partition/queue）");
        result.put("事件", events);
        return result;
    }

    /**
     * 事务消息 - 半消息 + 本地事务 + 二次确认
     * RocketMQ 的事务消息是分布式事务消息方案的经典实现
     */
    public Map<String, Object> sendTransaction() {
        Map<String, Object> result = new HashMap<>();
        List<String> events = new ArrayList<>();

        String msg = "事务消息-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            // 构造事务消息
            Message<String> message = MessageBuilder.withPayload(msg)
                    .setHeader("orderId", "TX-ORDER-" + System.currentTimeMillis())
                    .build();

            // 发送事务消息（半消息 -> 执行本地事务 -> 提交/回滚）
            TransactionSendResult txResult = rocketMQTemplate.sendMessageInTransaction(
                    RocketmqConfig.TRANSACTION_TOPIC, message, "本地事务参数");

            events.add("发送状态: " + txResult.getSendStatus());
            events.add("本地事务执行结果: " + txResult.getLocalTransactionState());

            log.info("✅ [TX] 事务消息发送完成: sendStatus={}, localTxState={}",
                    txResult.getSendStatus(), txResult.getLocalTransactionState());
        } catch (Exception e) {
            events.add("❌ 事务消息发送失败: " + e.getMessage());
            log.error("🚫 [TX] 事务消息失败: {}", e.getMessage());
        }

        result.put("事件", events);
        result.put("流程说明", "1.发送半消息(消费者不可见) -> 2.执行本地事务 -> 3.根据结果提交/回滚半消息");
        result.put("与Kafka对比", "Kafka事务保证原子写入多分区，RocketMQ事务保证本地事务+消息发送的一致性");
        result.put("注意", "需要在 RocketmqTransactionListener 中实现本地事务逻辑和回查逻辑");
        return result;
    }

    /**
     * 消息过滤 - Tag 过滤
     * 消费者可以只订阅特定 Tag 的消息
     */
    public Map<String, Object> sendFilter() {
        List<String> events = new ArrayList<>();
        String[] tags = {"TAG_ORDER", "TAG_PAYMENT", "TAG_NOTIFY"};

        for (int i = 0; i < 9; i++) {
            String tag = tags[i % tags.length];
            String msg = "过滤消息-" + tag + "-" + i;

            // 发送时指定 Tag（destination 格式: topic:tag）
            rocketMQTemplate.convertAndSend(RocketmqConfig.FILTER_TOPIC + ":" + tag, msg);
            events.add("发送: " + msg + " (tag=" + tag + ")");
            log.info("✅ [Filter] {}", msg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("Tag列表", List.of(tags));
        result.put("说明", "消费者订阅 demo_rocketmq_filter_topic:TAG_ORDER 只会收到 TAG_ORDER 的消息");
        result.put("与Kafka对比", "Kafka 没有原生 Tag 过滤，需要在消费端手动过滤或使用 Header 过滤");
        result.put("与RabbitMQ对比", "RabbitMQ 的 Topic 交换机用通配符实现类似效果，但路由发生在 Broker 端");
        result.put("事件", events);
        return result;
    }
}
