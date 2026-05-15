package com.example.kafka.work;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * @author hulei
 * @since 2026/5/15 12:51
 */

@Slf4j
@RestController
@Configuration
public class WorkService {

    public NewTopic probeTopic() {
        return new NewTopic(
                // 分区名
                "probe-topic",
                // 分区数
                4,
                // 副本数
                (short) 1);
    }

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @GetMapping("/test")
    public void test() {
        for (int i = 0; i < 10; i++) {
            int index = i % 4;
            switch (index) {
                case 0:
                    send("A" + i, "A" + i);
                    break;
                case 1:
                    send("B" + i, "B" + i);
                    break;
                case 2:
                    send("C" + i, "C" + i);
                    break;
                case 3:
                    send("D" + i, "D" + i);
                    break;
            }
        }
    }


    public void send(String key, String message) {

        int partition = 3;

        if (StringUtils.isNotBlank(key)) {
            if (key.startsWith("A")) {
                partition = 0;
            } else if (key.startsWith("B")) {
                partition = 1;
            } else if (key.startsWith("C")) {
                partition = 2;
            }
        }

        CompletableFuture<SendResult<String, Object>> send = kafkaTemplate.send(
                "probe-topic",
                partition,
                key,
                message
        );
    }


    @KafkaListener(topics = "probe-topic", containerFactory = "manualAckContainerFactory")
    public void consumer(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        String key = record.key();
        int partition = record.partition();
        long offset = record.offset();

        log.info("key: {}, partition: {}, offset: {}", key, partition, offset);
        log.info("message: {}", record.value());

        // 提交偏移量
        acknowledgment.acknowledge();
    }
}
