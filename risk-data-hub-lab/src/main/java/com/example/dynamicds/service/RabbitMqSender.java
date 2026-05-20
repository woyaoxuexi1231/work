package com.example.dynamicds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RabbitMQ 消息发送服务 — 同步任务全部成功时向下游发送完成通知。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RabbitMqSender {

    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "risk.sync.exchange";
    private static final String ROUTING_KEY = "risk.sync.completed";

    /**
     * 发送同步完成消息。
     */
    public void sendSyncCompleted(String taskId, String dataSourceKey,
                                  String datasourceType, int totalPulled, int totalSaved) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("taskId", taskId);
        message.put("dataSourceKey", dataSourceKey);
        message.put("datasourceType", datasourceType);
        message.put("totalPulledCount", totalPulled);
        message.put("totalSavedCount", totalSaved);
        message.put("status", "SUCCESS");
        message.put("timestamp", System.currentTimeMillis());

        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
            log.info("[RabbitMQ] 同步完成消息已发送 taskId={}, dataSourceKey={}, pulled={}, saved={}",
                    taskId, dataSourceKey, totalPulled, totalSaved);
        } catch (Exception e) {
            log.error("[RabbitMQ] 发送同步完成消息失败 taskId={}", taskId, e);
        }
    }
}
