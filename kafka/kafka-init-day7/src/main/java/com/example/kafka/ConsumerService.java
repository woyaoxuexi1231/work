package com.example.kafka;

import com.example.kafka.dto.ProductV2;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * @author hulei
 * @since 2026/5/18 13:42
 */

@Slf4j
@Service
public class ConsumerService {

    @KafkaListener(topics = "product-update")
    public void receive(ConsumerRecord<String, ProductV2> record, Acknowledgment acknowledgment) {
        log.info("收到消息：{}", record);
        acknowledgment.acknowledge();
    }
}
