package com.example.kafka;

import com.example.kafka.dto.Order;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConsumerConfig {

    /**
     * 为手动提交偏移量提供 containerFactory
     * 并发数（concurrency）表示消费者线程数，最大不超过分区数，此处设为 3
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Order> manualAckContainerFactory(
            ConsumerFactory<String, Order> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Order> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);  // 3 个消费者线程
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        // 可选：设置批量消费（一次 poll 返回多条）
        // factory.setBatchListener(true);
        return factory;
    }
}