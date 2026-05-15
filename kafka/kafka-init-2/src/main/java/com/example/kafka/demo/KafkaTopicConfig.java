package com.example.kafka.demo;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    /**
     * 创建一个名为 "orders" 的主题
     * partitions = 3：3个分区，可以让最多3个消费者并行消费
     * replicas = 1：仅用于单机测试，生产环境通常 >= 2
     */
    @Bean
    public NewTopic ordersTopic() {
        return new NewTopic("orders", 3, (short) 1);
    }

    /**
     * 另一个主题，用于演示偏移量重置
     */
    @Bean
    public NewTopic userActionsTopic() {
        return new NewTopic("user-actions", 2, (short) 1);
    }

    /**
     * probe-topic：4个分区，用于学习分区路由
     */
    @Bean
    public NewTopic probeTopic() {
        return new NewTopic("probe-topic", 4, (short) 1);
    }
}