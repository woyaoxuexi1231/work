package com.demo.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 配置类
 * 通过 TopicBuilder 声明所有演示所需的 Topic
 */
@Slf4j
@Configuration
public class KafkaConfig {

    // ==================== 基础收发 Topic ====================

    /** 基础 Topic - 3个分区，支持并行消费 */
    public static final String BASIC_TOPIC = "demo.basic.topic";

    @Bean
    public NewTopic basicTopic() {
        return TopicBuilder.name(BASIC_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** 带 Key 路由的 Topic - 同 Key 的消息保证进入同一分区（顺序保证） */
    public static final String KEY_ROUTE_TOPIC = "demo.key.route.topic";

    @Bean
    public NewTopic keyRouteTopic() {
        return TopicBuilder.name(KEY_ROUTE_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ==================== 性能压测 Topic ====================

    public static final String BENCH_TOPIC = "demo.bench.topic";

    @Bean
    public NewTopic benchTopic() {
        return TopicBuilder.name(BENCH_TOPIC)
                .partitions(6)
                .replicas(1)
                // 增大 segment 大小，提升写入性能
                .config("segment.bytes", String.valueOf(1024 * 1024 * 128))
                .build();
    }

    // ==================== 可靠性 Topic ====================

    public static final String RELIABLE_TOPIC = "demo.reliable.topic";

    @Bean
    public NewTopic reliableTopic() {
        return TopicBuilder.name(RELIABLE_TOPIC)
                .partitions(3)
                .replicas(1)
                // min.insync.replicas=1（单节点环境下只能设为1）
                .config("min.insync.replicas", "1")
                .build();
    }

    // ==================== 高级特性 Topic ====================

    /** 顺序消息 Topic */
    public static final String ORDER_TOPIC = "demo.order.topic";

    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name(ORDER_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** 事务消息 Topic */
    public static final String TRANSACTION_TOPIC = "demo.transaction.topic";

    @Bean
    public NewTopic transactionTopic() {
        return TopicBuilder.name(TRANSACTION_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** 压缩对比 Topic */
    public static final String COMPRESS_TOPIC = "demo.compress.topic";

    @Bean
    public NewTopic compressTopic() {
        return TopicBuilder.name(COMPRESS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
