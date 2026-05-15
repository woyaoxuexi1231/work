package com.example.kafka.demo;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OrderConsumerService {

    /**
     * 消费 orders 主题的所有分区
     * groupId 为 "order-group"，偏移量自动提交（默认提交间隔 5s）
     * 这里展示手动 ack，需要配置 spring.kafka.consumer.enable-auto-commit=false
     */
    @KafkaListener(topics = "orders", groupId = "order-group", containerFactory = "manualAckContainerFactory")
    public void consumeOrder(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment ack) {
        System.out.printf("收到消息: key=%s, partition=%d, offset=%d, value=%s%n",
                record.key(), record.partition(), record.offset(), record.value());

        // 模拟业务处理
        // ... 

        // 手动提交当前偏移量
        ack.acknowledge();
        System.out.println("手动提交偏移量完成");
    }

    /**
     * 演示从指定偏移量开始消费：通过配置 auto-offset-reset = earliest
     * 并且不提交偏移量，每次重启都会从头消费
     */
    @KafkaListener(topics = "user-actions", groupId = "reset-group",
            properties = {"spring.kafka.consumer.auto-offset-reset=earliest"})
    public void consumeUserAction(ConsumerRecord<String, Object> record) {
        System.out.printf("user-actions - partition=%d, offset=%d, value=%s%n",
                record.partition(), record.offset(), record.value());
    }
}