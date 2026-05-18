package com.example.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotentProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 由于开启了 enable.idempotence=true，即使网络重试也不会产生重复消息
     */
    public void sendIdempotent(String topic, String key, Object message) {
        kafkaTemplate.send(topic, key, message).whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.printf("幂等发送成功: offset=%d%n", result.getRecordMetadata().offset());
            } else {
                System.err.println("发送失败（但不会重复）: " + ex.getMessage());
            }
        });
    }
}