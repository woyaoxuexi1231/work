package com.demo.rabbitmq.producer;

import com.demo.rabbitmq.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RabbitMQ 生产者
 * 演示基础收发、性能压测、可靠性保障、高级特性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitProducer {

    private final RabbitTemplate rabbitTemplate;

    // ==================== 基础收发 ====================

    /**
     * Direct 模式发送 - 精确路由
     * 消息通过 routingKey 精确匹配到绑定的队列
     */
    public Map<String, Object> sendDirect(int count) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String msg = "Direct消息-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            rabbitTemplate.convertAndSend(RabbitConfig.DIRECT_EXCHANGE, RabbitConfig.DIRECT_ROUTING_KEY, msg);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ Direct模式发送 {} 条消息，耗时 {}ms", count, elapsed);
        Map<String, Object> result = new HashMap<>();
        result.put("模式", "Direct");
        result.put("发送数量", count);
        result.put("耗时ms", elapsed);
        return result;
    }

    /**
     * Topic 模式发送 - 通配符路由
     * order.create 和 order.pay 会匹配 order.* 和 #
     * user.login 只会匹配 #
     */
    public Map<String, Object> sendTopic(int count) {
        long start = System.currentTimeMillis();
        String[] routingKeys = {"order.create", "order.pay", "user.login"};
        for (int i = 0; i < count; i++) {
            String rk = routingKeys[i % routingKeys.length];
            String msg = "Topic消息-" + rk + "-" + i;
            rabbitTemplate.convertAndSend(RabbitConfig.TOPIC_EXCHANGE, rk, msg);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ Topic模式发送 {} 条消息，耗时 {}ms", count, elapsed);
        Map<String, Object> result = new HashMap<>();
        result.put("模式", "Topic");
        result.put("发送数量", count);
        result.put("路由键", List.of(routingKeys));
        result.put("耗时ms", elapsed);
        return result;
    }

    /**
     * Fanout 模式发送 - 广播
     * 消息会被投递到所有绑定的队列，忽略 routingKey
     */
    public Map<String, Object> sendFanout(int count) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String msg = "Fanout消息-" + i;
            rabbitTemplate.convertAndSend(RabbitConfig.FANOUT_EXCHANGE, "", msg);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ Fanout模式发送 {} 条消息，耗时 {}ms（每个消息会被2个队列各消费一次）", count, elapsed);
        Map<String, Object> result = new HashMap<>();
        result.put("模式", "Fanout（广播）");
        result.put("发送数量", count);
        result.put("消费队列数", 2);
        result.put("耗时ms", elapsed);
        return result;
    }

    // ==================== 性能压测 ====================

    /**
     * 性能压测 - 只管发，不等待确认，纯测吞吐量
     * （实际收到多少条请去 RabbitMQ Management 看）
     */
    public Map<String, Object> benchmark(int count, int size) {
        String payload = "X".repeat(Math.max(1, size));

        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            rabbitTemplate.convertAndSend(RabbitConfig.BENCH_EXCHANGE, RabbitConfig.BENCH_ROUTING_KEY, payload);
        }
        long elapsed = System.currentTimeMillis() - start;

        double throughput = count * 1000.0 / Math.max(1, elapsed);

        log.info("✅ RabbitMQ压测完成: 发送{}条(每条{}字节), 耗时{}ms, 吞吐量={}/s",
                count, size, elapsed, String.format("%.0f", throughput));

        Map<String, Object> result = new HashMap<>();
        result.put("消息数量", count);
        result.put("消息大小字节", size);
        result.put("总耗时ms", elapsed);
        result.put("吞吐量每秒", String.format("%.0f", throughput));
        result.put("说明", "实际收到数量请查看RabbitMQ Management");
        return result;
    }

    // ==================== 可靠性保障 ====================

    /**
     * 可靠性发送 - Publisher Confirm + Return
     * 开启 publisher-confirm-type=correlated 后，
     * 每条消息都会收到 broker 的确认回调
     */
    public Map<String, Object> sendReliable() {
        Map<String, Object> result = new HashMap<>();
        List<String> events = new ArrayList<>();

        // 场景1: 正常发送 - 使用 CorrelationData.getFuture() 等待确认
        CorrelationData cd1 = new CorrelationData("reliable-normal-" + UUID.randomUUID());
        rabbitTemplate.convertAndSend(RabbitConfig.RELIABLE_EXCHANGE, RabbitConfig.RELIABLE_ROUTING_KEY,
                "可靠性测试-正常消息", cd1);
        try {
            CorrelationData.Confirm confirm = cd1.getFuture().get(2, TimeUnit.SECONDS);
            String event = confirm.isAck()
                    ? "✅ 消息确认成功: " + cd1.getId()
                    : "❌ 消息确认失败: " + cd1.getId() + ", 原因: " + confirm.getReason();
            log.info(event);
            events.add(event);
        } catch (Exception e) {
            String event = "❌ 消息确认超时/异常: " + cd1.getId() + ", " + e.getMessage();
            log.error(event);
            events.add(event);
        }

        // 场景2: 发送到不存在的 routingKey - 消息无法路由
        CorrelationData cd2 = new CorrelationData("reliable-unroutable-" + UUID.randomUUID());
        rabbitTemplate.convertAndSend(RabbitConfig.RELIABLE_EXCHANGE, "non.existent.key",
                "可靠性测试-不可路由消息", cd2);
        try {
            CorrelationData.Confirm confirm = cd2.getFuture().get(2, TimeUnit.SECONDS);
            String event = confirm.isAck()
                    ? "✅ 消息确认成功: " + cd2.getId()
                    : "❌ 消息确认失败: " + cd2.getId() + ", 原因: " + confirm.getReason();
            log.info(event);
            events.add(event);
        } catch (Exception e) {
            String event = "❌ 消息确认超时/异常: " + cd2.getId() + ", " + e.getMessage();
            log.error(event);
            events.add(event);
        }

        result.put("说明", "Publisher Confirm: 使用CorrelationData.getFuture()逐条等待broker确认");
        result.put("事件列表", events);
        return result;
    }

    // ==================== 高级特性 ====================

    /**
     * 延迟消息 - 利用 TTL + 死信交换机实现
     * 消息先发到延迟队列（设有TTL），过期后被投递到死信交换机 -> 消费队列
     *
     * @param delayMs 延迟毫秒数（消息级别TTL，覆盖队列默认TTL）
     */
    public Map<String, Object> sendDelay(int delayMs) {
        String msg = "延迟消息-" + UUID.randomUUID().toString().substring(0, 8) + "-延迟" + delayMs + "ms";

        MessagePostProcessor processor = message -> {
            message.getMessageProperties().setExpiration(String.valueOf(delayMs));
            return message;
        };

        long sendTime = System.currentTimeMillis();
        // 发到延迟队列（没有消费者），TTL 过期后通过 DLX 路由到消费队列
        rabbitTemplate.convertAndSend(RabbitConfig.DIRECT_EXCHANGE, RabbitConfig.DIRECT_ROUTING_KEY, "",
                processor);

        // 实际需要发到延迟队列 - 使用专用交换机绑定
        // 这里简化：直接发到延迟队列（通过默认交换机）
        rabbitTemplate.convertAndSend("", RabbitConfig.DELAY_QUEUE, msg, processor);

        log.info("✅ 延迟消息已发送: 延迟{}ms, 消息={}", delayMs, msg);
        Map<String, Object> result = new HashMap<>();
        result.put("延迟毫秒", delayMs);
        result.put("消息", msg);
        result.put("说明", "消息在延迟队列中等待TTL过期，然后通过DLX路由到消费队列被消费");
        result.put("发送时间戳", sendTime);
        return result;
    }

    /**
     * 死信队列演示 - 发送消息到业务队列，通过 nack 拒绝消息使其进入死信队列
     */
    public Map<String, Object> sendToDeadLetter() {
        String msg = "将被拒绝的消息-" + UUID.randomUUID().toString().substring(0, 8);
        rabbitTemplate.convertAndSend(RabbitConfig.DIRECT_EXCHANGE, RabbitConfig.BIZ_ROUTING_KEY, msg);

        log.info("✅ 消息发送到业务队列(配置了DLX): {}，消费者将拒绝此消息使其进入死信队列", msg);
        Map<String, Object> result = new HashMap<>();
        result.put("消息", msg);
        result.put("说明", "消息发到业务队列 -> 消费者basicNack拒绝 -> 消息路由到死信交换机 -> 进入死信队列");
        return result;
    }

    /**
     * 优先级队列 - 发送不同优先级的消息
     * 高优先级消息会先被消费（前提是队列中有积压）
     */
    public Map<String, Object> sendPriority() {
        List<String> events = new ArrayList<>();

        // 先发低优先级消息
        for (int i = 0; i < 5; i++) {
            MessagePostProcessor low = message -> {
                message.getMessageProperties().setPriority(1);
                return message;
            };
            rabbitTemplate.convertAndSend(RabbitConfig.DIRECT_EXCHANGE, RabbitConfig.PRIORITY_ROUTING_KEY,
                    "低优先级消息-" + i, low);
            events.add("发送: 低优先级消息-" + i + " (priority=1)");
        }

        // 再发高优先级消息
        for (int i = 0; i < 5; i++) {
            MessagePostProcessor high = message -> {
                message.getMessageProperties().setPriority(10);
                return message;
            };
            rabbitTemplate.convertAndSend(RabbitConfig.DIRECT_EXCHANGE, RabbitConfig.PRIORITY_ROUTING_KEY,
                    "高优先级消息-" + i, high);
            events.add("发送: 高优先级消息-" + i + " (priority=10)");
        }

        log.info("✅ 优先级消息已发送: 5条低优先级(priority=1) + 5条高优先级(priority=10)");
        Map<String, Object> result = new HashMap<>();
        result.put("说明", "高优先级消息(priority=10)会先于低优先级消息(priority=1)被消费");
        result.put("注意", "优先级仅在队列有积压时生效，如果消费者消费速度很快则无区别");
        result.put("事件", events);
        return result;
    }
}
