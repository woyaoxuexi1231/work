package com.demo.rabbitmq.consumer;

import com.demo.rabbitmq.config.RabbitConfig;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * RabbitMQ 消费者
 * 使用手动 ACK 模式，演示可靠性消费
 */
@Slf4j
@Component
public class RabbitConsumer {

    // ==================== Direct 模式消费 ====================

    @RabbitListener(queues = RabbitConfig.DIRECT_QUEUE)
    public void consumeDirect(String message, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.debug("✅ [Direct] 收到消息: {}", message);
        // 手动确认消息
        channel.basicAck(deliveryTag, false);
    }

    // ==================== Topic 模式消费 ====================

    @RabbitListener(queues = RabbitConfig.TOPIC_QUEUE_ALL)
    public void consumeTopicAll(String message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.debug("✅ [Topic-All] 收到消息: {}", message);
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = RabbitConfig.TOPIC_QUEUE_ORDER)
    public void consumeTopicOrder(String message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.debug("✅ [Topic-Order] 收到消息: {}", message);
        channel.basicAck(deliveryTag, false);
    }

    // ==================== Fanout 模式消费 ====================

    @RabbitListener(queues = RabbitConfig.FANOUT_QUEUE_A)
    public void consumeFanoutA(String message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.debug("✅ [Fanout-A] 收到消息: {}", message);
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = RabbitConfig.FANOUT_QUEUE_B)
    public void consumeFanoutB(String message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.debug("✅ [Fanout-B] 收到消息: {}", message);
        channel.basicAck(deliveryTag, false);
    }

    // ==================== 性能压测消费 ====================

    @RabbitListener(queues = RabbitConfig.BENCH_QUEUE)
    public void consumeBench(String message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        // 压测场景：快速消费，不打印日志
        channel.basicAck(deliveryTag, false);
    }

    // ==================== 可靠性消费 - 演示手动 ACK/NACK ====================

    @RabbitListener(queues = RabbitConfig.RELIABLE_QUEUE)
    public void consumeReliable(String message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            // 模拟业务处理
            log.info("✅ [Reliable] 处理消息: {}", message);
            // 处理成功 -> 确认
            channel.basicAck(deliveryTag, false);
            log.info("✅ [Reliable] 消息已确认: {}", message);
        } catch (Exception e) {
            log.error("🚫 [Reliable] 消息处理失败: {}", e.getMessage());
            // 处理失败 -> nack，requeue=true 会重新入队
            channel.basicNack(deliveryTag, false, true);
            log.warn("⚠️ [Reliable] 消息已拒绝并重新入队");
        }
    }

    // ==================== 业务队列消费 - 演示死信 ====================

    /**
     * 消费业务队列：故意拒绝消息使其进入死信队列
     */
    @RabbitListener(queues = RabbitConfig.BIZ_QUEUE_WITH_DLX)
    public void consumeBizWithDlx(String message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("⚠️ [Biz-DLX] 收到消息，故意拒绝使其进入死信队列: {}", message);
        // basicNack + requeue=false -> 消息进入死信队列
        channel.basicNack(deliveryTag, false, false);
        log.info("✅ [Biz-DLX] 消息已拒绝(requeue=false)，将路由到死信队列");
    }

    /**
     * 消费死信队列
     */
    @RabbitListener(queues = RabbitConfig.DLX_QUEUE)
    public void consumeDlx(String message, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("🚫 [DLX] 死信队列收到消息: {}", message);
        channel.basicAck(deliveryTag, false);
        log.info("✅ [DLX] 死信消息已处理");
    }

    // ==================== 延迟消息消费 ====================

    @RabbitListener(queues = RabbitConfig.DELAY_CONSUME_QUEUE)
    public void consumeDelay(String message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("✅ [Delay] 延迟消息已到达消费队列: {}", message);
        channel.basicAck(deliveryTag, false);
    }

    // ==================== 优先级队列消费 ====================

    @RabbitListener(queues = RabbitConfig.PRIORITY_QUEUE)
    public void consumePriority(String message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                @Header(value = "x-received-priority", required = false) Integer priority) throws IOException {
        log.info("✅ [Priority] 消费消息(priority={}): {}", priority, message);
        channel.basicAck(deliveryTag, false);
    }
}
