package com.example.kafka;

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

    @Bean
    public NewTopic ordersTopic2() {
        return new NewTopic("orders-string", 3, (short) 1);
    }

}