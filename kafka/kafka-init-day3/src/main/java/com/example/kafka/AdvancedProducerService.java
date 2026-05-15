package com.example.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AdvancedProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // 1. 发后即忘（fire-and-forget）：不关心结果，性能最高，可能丢失消息
    public void sendFireAndForget(String topic, String key, Object message) {
        kafkaTemplate.send(topic, key, message);
        System.out.println("发后即忘模式，不等待结果");
    }

    // 2. 同步发送：阻塞直到收到响应，确保消息已发送，但吞吐低
    public void sendSync(String topic, String key, Object message)
            throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, message).toCompletableFuture();
        // 最多等待 10 秒
        SendResult<String, Object> result = future.get(10, TimeUnit.SECONDS);
        System.out.printf("同步发送成功: offset=%d, partition=%d%n",
                result.getRecordMetadata().offset(),
                result.getRecordMetadata().partition());
    }

    // 3. 异步发送（带回调）：推荐方式，不阻塞，能处理结果
    public void sendAsync(String topic, String key, Object message) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, message).toCompletableFuture();
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.printf("异步发送成功: offset=%d, partition=%d%n",
                        result.getRecordMetadata().offset(),
                        result.getRecordMetadata().partition());
            } else {
                System.err.printf("异步发送失败: %s, key=%s, message=%s%n",
                        ex.getMessage(), key, message);
                // 可在此进行重试逻辑或记录死信
            }
        });
    }
}