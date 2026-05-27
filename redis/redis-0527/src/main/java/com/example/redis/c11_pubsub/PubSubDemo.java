package com.example.redis.c11_pubsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 11. 发布订阅（Pub/Sub）
 * <p>
 * Pub/Sub 是 Redis 的消息发布订阅机制。
 * 发布者向频道发送消息，所有订阅该频道的客户端都会收到。
 * <p>
 * 核心命令：
 * - SUBSCRIBE channel [channel ...]: 订阅频道
 * - UNSUBSCRIBE [channel [channel ...]]: 取消订阅
 * - PUBLISH channel message: 发布消息
 * - PSUBSCRIBE pattern [pattern ...]: 模式订阅（支持通配符）
 * - PUNSUBSCRIBE [pattern [pattern ...]]: 取消模式订阅
 * <p>
 * 特性：
 * - 消息不持久化：订阅者断开期间的消息会丢失
 * - 无消费者组：每个订阅者收到所有消息（广播模式）
 * - fire-and-forget：发布者不关心是否有订阅者
 * <p>
 * 局限性：
 * - 不适合需要可靠投递的场景（用 Stream 替代）
 * - 不适合需要消息持久化的场景
 * - 不适合需要负载均衡的场景（每个订阅者收到所有消息）
 * <p>
 * 适用场景：
 * - 实时通知（如聊天室、实时数据推送）
 * - 配置变更广播
 * - 事件总线（非关键事件）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PubSubDemo {

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    /**
     * Pub/Sub 基础演示
     * <p>
     * 使用 Spring 的 RedisMessageListenerContainer 管理订阅。
     * 这是 Spring 推荐的方式，它处理了：
     * - 连接管理
     * - 线程模型
     * - 异常恢复
     */
    public String basicPubSub() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        var receivedMessages = new java.util.concurrent.ConcurrentLinkedQueue<String>();

        // 注册监听器
        MessageListener listener = (message, pattern) -> {
            String msg = new String(message.getBody());
            String channel = new String(message.getChannel());
            log.info("[订阅者] 收到消息: channel={}, message={}", channel, msg);
            receivedMessages.add(msg);
            latch.countDown();
        };

        listenerContainer.addMessageListener(listener, new ChannelTopic("pubsub:demo"));

        // 等待订阅建立
        Thread.sleep(100);

        // 发布消息
        for (int i = 1; i <= 3; i++) {
            Long receivers = redisTemplate.convertAndSend("pubsub:demo", "消息-" + i);
            log.info("[发布者] 发送 '消息-{}', 接收者数量={}", i, receivers);
            Thread.sleep(50);
        }

        // 等待消息接收
        latch.await(5, TimeUnit.SECONDS);

        // 移除监听器
        listenerContainer.removeMessageListener(listener);

        log.info("[Pub/Sub] 共收到 {} 条消息", receivedMessages.size());
        return "收到消息=" + receivedMessages.size();
    }

    /**
     * Pub/Sub 多频道订阅
     * <p>
     * 一个监听器可以同时订阅多个频道。
     * 不同频道的消息通过 channel 字段区分。
     */
    public String multiChannel() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(4);
        var messages = new java.util.concurrent.ConcurrentLinkedQueue<String>();

        MessageListener listener = (message, pattern) -> {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());
            messages.add(channel + ":" + body);
            log.info("[多频道] channel={}, msg={}", channel, body);
            latch.countDown();
        };

        // 同时订阅两个频道
        listenerContainer.addMessageListener(listener,
                new ChannelTopic("pubsub:order"));
        listenerContainer.addMessageListener(listener,
                new ChannelTopic("pubsub:payment"));

        Thread.sleep(100);

        redisTemplate.convertAndSend("pubsub:order", "新订单-1001");
        redisTemplate.convertAndSend("pubsub:payment", "支付成功-1001");
        redisTemplate.convertAndSend("pubsub:order", "新订单-1002");
        redisTemplate.convertAndSend("pubsub:payment", "退款-1001");

        latch.await(5, TimeUnit.SECONDS);
        listenerContainer.removeMessageListener(listener);

        log.info("[多频道] 共收到 {} 条消息", messages.size());
        return "消息=" + messages;
    }
}
