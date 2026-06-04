package com.example.kafka.demo;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OrderProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // 模拟自定义分区器：根据订单金额决定分区
    // 金额 < 100 到分区0，100~500 到分区1，>500 到分区2
    public void sendOrderWithCustomPartition(String orderId, double amount) {
        int partition;
        if (amount < 100) {
            partition = 0;
        } else if (amount <= 500) {
            partition = 1;
        } else {
            partition = 2;
        }

        // 构造 ProducerRecord 时可以指定分区
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                "orders",
                partition,
                orderId,
                new java.util.HashMap<String, Object>() {{
                    put("orderId", orderId);
                    put("amount", amount);
                }}
        );

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.printf("消息发送成功: topic=%s, partition=%d, offset=%d%n",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                System.err.printf("发送失败: %s%n", ex.getMessage());
            }
        });
    }

    // 不指定分区时，Kafka 默认分区策略：
    // 如果 key 不为 null，使用 key 的 hash 对分区数取模；
    // 如果 key 为 null，使用轮询（sticky partition）。
    public void sendOrderWithKey(String orderId, double amount) {
                java.util.Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("orderId", orderId);
        msg.put("amount", amount);
        kafkaTemplate.send("orders", orderId, msg);
    }
}