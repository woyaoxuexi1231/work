package com.example.kafka;

import com.example.kafka.dto.Order;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author hulei
 * @since 2026/5/15 15:36
 */

@RestController
public class ProducerController {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @GetMapping("/test")
    public void test() {
        for (int i = 0; i < 10; i++) {
            // Map<String, Object> order = Map.of(
            //         "orderId", "order-" + i,
            //         "amount", 100.0 * i,
            //         "timestamp", System.currentTimeMillis()
            // );
            Order order1 = new Order("order-" + i, 100.0 * i, System.currentTimeMillis());
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    "orders",
                    "order-" + i,
                    order1
            );
            kafkaTemplate.send(record);
        }
    }
}
