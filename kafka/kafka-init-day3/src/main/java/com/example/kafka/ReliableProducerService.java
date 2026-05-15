package com.example.kafka;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * @author hulei
 * @since 2026/5/15 13:43
 */

@Slf4j
@Service
public class ReliableProducerService {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    public void sendWithRetryRecord(String key, String value) {
        CompletableFuture<SendResult<String, Object>> send = kafkaTemplate.send("retry-demo-topic", key, value);
        send.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("消息发送成功");
            } else {
                log.info("发送失败, ", ex);
                JSONObject jsonObject = JSON.parseObject(value);
                jsonObject.put("failureReason", ex.getMessage());
                kafkaTemplate.send("retry-demo-dlq", key, jsonObject.toJSONString());
            }
        });
    }
}
