package com.example.kafka;

import com.example.kafka.dto.Order;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
            // Map<String, Object> order = new java.util.HashMap<String, Object>() {{    put(//         "orderId", "order-" + i);    put(//         "amount", 100.0 * i);    put(//         "timestamp", System.currentTimeMillis();}}
            // );
            Order order1 = new Order("order-" + i, 100.0 * i + 500, System.currentTimeMillis(), 0);
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    "order-processing",
                    "order-" + i,
                    order1
            );
            kafkaTemplate.send(record);
        }
    }
}
