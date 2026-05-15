package com.example.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ContinuousProducer implements CommandLineRunner {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void run(String... args) throws Exception {
        // 每秒发送一条消息，用于观察再均衡时消费情况
        new Thread(() -> {
            int i = 0;
            while (true) {
                kafkaTemplate.send("orders-string", "key-" + (i%3), "Message " + i);
                i++;
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
            }
        }).start();
    }
}