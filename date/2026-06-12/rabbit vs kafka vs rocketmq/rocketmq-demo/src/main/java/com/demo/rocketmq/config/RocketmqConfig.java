package com.demo.rocketmq.config;

/**
 * RocketMQ 常量配置
 * RocketMQ 不需要像 Kafka/RabbitMQ 那样在代码中声明 Topic/Queue，
 * Topic 由生产者发送时自动创建，消费者订阅时指定 Topic 和 ConsumerGroup
 */
public final class RocketmqConfig {

    private RocketmqConfig() {}

    // ==================== 基础收发 Topic ====================

    public static final String BASIC_TOPIC = "demo_rocketmq_basic_topic";

    // ==================== 性能压测 Topic ====================

    public static final String BENCH_TOPIC = "demo_rocketmq_bench_topic";

    // ==================== 可靠性 Topic ====================

    public static final String RELIABLE_TOPIC = "demo_rocketmq_reliable_topic";

    // ==================== 高级特性 Topic ====================

    /** 延迟消息 Topic */
    public static final String DELAY_TOPIC = "demo_rocketmq_delay_topic";

    /** 顺序消息 Topic */
    public static final String ORDER_TOPIC = "demo_rocketmq_order_topic";

    /** 事务消息 Topic */
    public static final String TRANSACTION_TOPIC = "demo_rocketmq_transaction_topic";

    /** 消息过滤 Topic */
    public static final String FILTER_TOPIC = "demo_rocketmq_filter_topic";

    // ==================== 消费者组 ====================

    public static final String BASIC_CONSUMER_GROUP = "demo_basic_consumer_group";
    public static final String BENCH_CONSUMER_GROUP = "demo_bench_consumer_group";
    public static final String RELIABLE_CONSUMER_GROUP = "demo_reliable_consumer_group";
    public static final String DELAY_CONSUMER_GROUP = "demo_delay_consumer_group";
    public static final String ORDER_CONSUMER_GROUP = "demo_order_consumer_group";
    public static final String TX_CONSUMER_GROUP = "demo_tx_consumer_group";
    public static final String FILTER_CONSUMER_GROUP = "demo_filter_consumer_group";
}
