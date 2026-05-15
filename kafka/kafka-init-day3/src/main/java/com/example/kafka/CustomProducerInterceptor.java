package com.example.kafka;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;


public class CustomProducerInterceptor implements ProducerInterceptor<String, Object> {

    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        // 在消息发送前，添加一个自定义 header：timestamp_ms
        long now = System.currentTimeMillis();
        record.headers().add("send-timestamp", String.valueOf(now).getBytes());
        System.out.println("[Interceptor] 消息即将发送，key=" + record.key() + ", 添加时间戳header");
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        if (exception == null) {
            System.out.printf("[Interceptor] 发送成功: topic=%s, partition=%d, offset=%d%n",
                    metadata.topic(), metadata.partition(), metadata.offset());
        } else {
            System.err.println("[Interceptor] 发送失败: " + exception.getMessage());
        }
    }

    @Override
    public void close() {}

    @Override
    public void configure(Map<String, ?> configs) {}
}