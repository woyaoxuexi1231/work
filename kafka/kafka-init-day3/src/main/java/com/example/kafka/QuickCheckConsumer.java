package com.example.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

// 为了看到发送的效果，可简单消费 orders 主题打印
@Service
public class QuickCheckConsumer {
    @KafkaListener(topics = "orders", groupId = "check-group")
    public void listen(ConsumerRecord<String, Object> record) {
        System.out.printf("[Consumer] topic=%s, partition=%d, offset=%d, key=%s, value=%s%n",
                record.topic(), record.partition(), record.offset(), record.getKey(), record.value());
        // 打印我们添加的拦截器 header
        record.headers().forEach(header -> {
            if (header.getKey().equals("send-timestamp")) {
                String timestamp = new String(header.value());
                System.out.println("Header send-timestamp: " + timestamp);
            }
        });
    }
}