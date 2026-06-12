package com.demo.rocketmq.consumer;

import com.demo.rocketmq.config.RocketmqConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 消费者
 * 使用 @RocketMQMessageListener 注解声明消费者
 */
@Slf4j
@Component
public class RocketmqConsumer {

    // ==================== 基础消费（集群模式） ====================

    /**
     * 集群模式消费 - 同一 ConsumerGroup 中，每条消息只被一个消费者消费
     * 这是最常用的消费模式，相当于 Kafka 的消费者组
     */
    @Component
    @RocketMQMessageListener(
            topic = RocketmqConfig.BASIC_TOPIC,
            consumerGroup = RocketmqConfig.BASIC_CONSUMER_GROUP,
            consumeMode = ConsumeMode.CONCURRENTLY,
            messageModel = MessageModel.CLUSTERING
    )
    public static class BasicConsumer implements RocketMQListener<String> {
        @Override
        public void onMessage(String message) {
            log.debug("✅ [Basic] 收到消息: {}", message);
        }
    }

    // ==================== 性能压测消费 ====================

    @Component
    @RocketMQMessageListener(
            topic = RocketmqConfig.BENCH_TOPIC,
            consumerGroup = RocketmqConfig.BENCH_CONSUMER_GROUP,
            consumeMode = ConsumeMode.CONCURRENTLY
    )
    public static class BenchConsumer implements RocketMQListener<String> {
        @Override
        public void onMessage(String message) {
            // 压测场景：快速消费，不打印日志
        }
    }

    // ==================== 可靠性消费 ====================

    /**
     * 可靠性消费 - 顺序消费模式
     * ConsumeMode.ORDERLY: 保证同一队列的消息按顺序消费
     */
    @Component
    @RocketMQMessageListener(
            topic = RocketmqConfig.RELIABLE_TOPIC,
            consumerGroup = RocketmqConfig.RELIABLE_CONSUMER_GROUP,
            consumeMode = ConsumeMode.ORDERLY
    )
    public static class ReliableConsumer implements RocketMQListener<String> {
        @Override
        public void onMessage(String message) {
            try {
                log.info("✅ [Reliable] 处理消息: {}", message);
                // 处理成功，RocketMQ 自动返回 CONSUME_SUCCESS
                // 如果抛异常，会自动重试（最多16次）
            } catch (Exception e) {
                log.error("🚫 [Reliable] 处理失败: {}", e.getMessage());
                throw e; // 抛异常触发重试
            }
        }
    }

    // ==================== 延迟消息消费 ====================

    @Component
    @RocketMQMessageListener(
            topic = RocketmqConfig.DELAY_TOPIC,
            consumerGroup = RocketmqConfig.DELAY_CONSUMER_GROUP
    )
    public static class DelayConsumer implements RocketMQListener<String> {
        @Override
        public void onMessage(String message) {
            log.info("✅ [Delay] 延迟消息已到达: {}", message);
        }
    }

    // ==================== 顺序消息消费 ====================

    /**
     * 顺序消费 - 使用 ORDERLY 模式
     * 保证同一 MessageQueue 的消息按顺序被消费
     */
    @Component
    @RocketMQMessageListener(
            topic = RocketmqConfig.ORDER_TOPIC,
            consumerGroup = RocketmqConfig.ORDER_CONSUMER_GROUP,
            consumeMode = ConsumeMode.ORDERLY
    )
    public static class OrderConsumer implements RocketMQListener<String> {
        @Override
        public void onMessage(String message) {
            log.info("✅ [Order] 收到顺序消息: {}", message);
        }
    }

    // ==================== 事务消息消费 ====================

    @Component
    @RocketMQMessageListener(
            topic = RocketmqConfig.TRANSACTION_TOPIC,
            consumerGroup = RocketmqConfig.TX_CONSUMER_GROUP
    )
    public static class TransactionConsumer implements RocketMQListener<String> {
        @Override
        public void onMessage(String message) {
            log.info("✅ [TX] 收到事务消息: {}", message);
        }
    }

    // ==================== Tag 过滤消费 ====================

    /**
     * Tag 过滤消费 - 只消费 TAG_ORDER 标签的消息
     * selectorExpression = "TAG_ORDER" 实现 Tag 级别过滤
     */
    @Component
    @RocketMQMessageListener(
            topic = RocketmqConfig.FILTER_TOPIC,
            consumerGroup = RocketmqConfig.FILTER_CONSUMER_GROUP,
            selectorExpression = "TAG_ORDER"
    )
    public static class FilterConsumer implements RocketMQListener<String> {
        @Override
        public void onMessage(String message) {
            log.info("✅ [Filter] 收到 TAG_ORDER 消息: {}", message);
        }
    }
}
