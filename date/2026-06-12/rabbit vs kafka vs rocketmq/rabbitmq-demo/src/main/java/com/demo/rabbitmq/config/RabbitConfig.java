package com.demo.rabbitmq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类
 * 声明所有演示所需的队列、交换机和绑定关系
 */
@Configuration
public class RabbitConfig {

    // ==================== 基础收发 - Direct 模式 ====================

    public static final String DIRECT_EXCHANGE = "demo.direct.exchange";
    public static final String DIRECT_QUEUE = "demo.direct.queue";
    public static final String DIRECT_ROUTING_KEY = "demo.direct.routingKey";

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public Queue directQueue() {
        return QueueBuilder.durable(DIRECT_QUEUE).build();
    }

    @Bean
    public Binding directBinding() {
        return BindingBuilder.bind(directQueue()).to(directExchange()).with(DIRECT_ROUTING_KEY);
    }

    // ==================== 基础收发 - Topic 模式 ====================

    public static final String TOPIC_EXCHANGE = "demo.topic.exchange";
    public static final String TOPIC_QUEUE_ALL = "demo.topic.queue.all";
    public static final String TOPIC_QUEUE_ORDER = "demo.topic.queue.order";

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public Queue topicQueueAll() {
        return QueueBuilder.durable(TOPIC_QUEUE_ALL).build();
    }

    @Bean
    public Queue topicQueueOrder() {
        return QueueBuilder.durable(TOPIC_QUEUE_ORDER).build();
    }

    @Bean
    public Binding topicBindingAll() {
        // # 匹配所有路由键
        return BindingBuilder.bind(topicQueueAll()).to(topicExchange()).with("#");
    }

    @Bean
    public Binding topicBindingOrder() {
        // 只匹配 order.* 的路由键
        return BindingBuilder.bind(topicQueueOrder()).to(topicExchange()).with("order.*");
    }

    // ==================== 基础收发 - Fanout 模式 ====================

    public static final String FANOUT_EXCHANGE = "demo.fanout.exchange";
    public static final String FANOUT_QUEUE_A = "demo.fanout.queue.a";
    public static final String FANOUT_QUEUE_B = "demo.fanout.queue.b";

    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange(FANOUT_EXCHANGE, true, false);
    }

    @Bean
    public Queue fanoutQueueA() {
        return QueueBuilder.durable(FANOUT_QUEUE_A).build();
    }

    @Bean
    public Queue fanoutQueueB() {
        return QueueBuilder.durable(FANOUT_QUEUE_B).build();
    }

    @Bean
    public Binding fanoutBindingA() {
        return BindingBuilder.bind(fanoutQueueA()).to(fanoutExchange());
    }

    @Bean
    public Binding fanoutBindingB() {
        return BindingBuilder.bind(fanoutQueueB()).to(fanoutExchange());
    }

    // ==================== 性能压测队列 ====================

    public static final String BENCH_EXCHANGE = "demo.bench.exchange";
    public static final String BENCH_QUEUE = "demo.bench.queue";
    public static final String BENCH_ROUTING_KEY = "demo.bench.routingKey";

    @Bean
    public DirectExchange benchExchange() {
        return new DirectExchange(BENCH_EXCHANGE, true, false);
    }

    @Bean
    public Queue benchQueue() {
        return QueueBuilder.durable(BENCH_QUEUE).build();
    }

    @Bean
    public Binding benchBinding() {
        return BindingBuilder.bind(benchQueue()).to(benchExchange()).with(BENCH_ROUTING_KEY);
    }

    // ==================== 可靠性演示队列 ====================

    public static final String RELIABLE_EXCHANGE = "demo.reliable.exchange";
    public static final String RELIABLE_QUEUE = "demo.reliable.queue";
    public static final String RELIABLE_ROUTING_KEY = "demo.reliable.routingKey";

    @Bean
    public DirectExchange reliableExchange() {
        return new DirectExchange(RELIABLE_EXCHANGE, true, false);
    }

    @Bean
    public Queue reliableQueue() {
        return QueueBuilder.durable(RELIABLE_QUEUE).build();
    }

    @Bean
    public Binding reliableBinding() {
        return BindingBuilder.bind(reliableQueue()).to(reliableExchange()).with(RELIABLE_ROUTING_KEY);
    }

    // ==================== 高级特性 - 死信队列 (DLX) ====================

    public static final String DLX_EXCHANGE = "demo.dlx.exchange";
    public static final String DLX_QUEUE = "demo.dlx.queue";
    public static final String DLX_ROUTING_KEY = "demo.dlx.routingKey";

    /**
     * 业务队列 - 配置死信交换机
     * 当消息被拒绝/过期/队列满时，会路由到死信交换机
     */
    public static final String BIZ_QUEUE_WITH_DLX = "demo.biz.queue.with.dlx";
    public static final String BIZ_ROUTING_KEY = "demo.biz.routingKey";

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }

    @Bean
    public Queue bizQueueWithDlx() {
        Map<String, Object> args = new HashMap<>();
        // 指定死信交换机
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        // 指定死信路由键
        args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);
        return QueueBuilder.durable(BIZ_QUEUE_WITH_DLX).withArguments(args).build();
    }

    @Bean
    public Binding bizBindingWithDlx() {
        return BindingBuilder.bind(bizQueueWithDlx()).to(directExchange()).with(BIZ_ROUTING_KEY);
    }

    // ==================== 高级特性 - 延迟消息 ====================

    /**
     * 延迟消息队列
     * 原理：利用 TTL + 死信交换机实现
     * 消息在延迟队列中等待 TTL 过期，然后被投递到死信交换机 -> 消费队列
     */
    public static final String DELAY_DEAD_EXCHANGE = "demo.delay.dead.exchange";
    public static final String DELAY_QUEUE = "demo.delay.queue";
    public static final String DELAY_CONSUME_QUEUE = "demo.delay.consume.queue";
    public static final String DELAY_CONSUME_ROUTING_KEY = "demo.delay.consume.routingKey";

    @Bean
    public DirectExchange delayDeadExchange() {
        return new DirectExchange(DELAY_DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue delayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DELAY_DEAD_EXCHANGE);
        args.put("x-dead-letter-routing-key", DELAY_CONSUME_ROUTING_KEY);
        // 队列默认 TTL（毫秒），也可在消息级别设置
        args.put("x-message-ttl", 5000);
        return QueueBuilder.durable(DELAY_QUEUE).withArguments(args).build();
    }

    @Bean
    public Queue delayConsumeQueue() {
        return QueueBuilder.durable(DELAY_CONSUME_QUEUE).build();
    }

    @Bean
    public Binding delayConsumeBinding() {
        return BindingBuilder.bind(delayConsumeQueue()).to(delayDeadExchange()).with(DELAY_CONSUME_ROUTING_KEY);
    }

    // ==================== 高级特性 - 优先级队列 ====================

    public static final String PRIORITY_QUEUE = "demo.priority.queue";
    public static final String PRIORITY_ROUTING_KEY = "demo.priority.routingKey";

    @Bean
    public Queue priorityQueue() {
        Map<String, Object> args = new HashMap<>();
        // 设置最大优先级为 10
        args.put("x-max-priority", 10);
        return QueueBuilder.durable(PRIORITY_QUEUE).withArguments(args).build();
    }

    @Bean
    public Binding priorityBinding() {
        return BindingBuilder.bind(priorityQueue()).to(directExchange()).with(PRIORITY_ROUTING_KEY);
    }
}
