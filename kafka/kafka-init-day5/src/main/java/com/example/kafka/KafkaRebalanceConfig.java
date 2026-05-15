package com.example.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaRebalanceConfig {

    // KafkaConsumerConfig.java
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> rebalanceAwareContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 注册我们更新后的监听器
        factory.getContainerProperties().setConsumerRebalanceListener(new CustomRebalanceListener());

        return factory;
    }

}