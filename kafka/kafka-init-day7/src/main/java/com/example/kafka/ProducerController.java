package com.example.kafka;

import com.example.kafka.dto.ProductV1;
import com.example.kafka.dto.ProductV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * @author hulei
 * @since 2026/5/18 13:39
 */

@RestController
public class ProducerController {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @GetMapping("/test")
    public void test() {
        kafkaTemplate.send(
                "product-update",
                "v1-" + System.currentTimeMillis(),
                new ProductV1(
                        "id-"+System.currentTimeMillis(),
                        new BigDecimal("10.0")
                )
        );

        kafkaTemplate.send(
                "product-update",
                "v2-" + System.currentTimeMillis(),
                new ProductV2(
                        "id-"+System.currentTimeMillis(),
                        new BigDecimal("10.0"),
                        "USD",
                        new BigDecimal("0.1")
                )
        );
    }
}
