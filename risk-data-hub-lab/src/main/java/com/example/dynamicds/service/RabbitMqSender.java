package com.example.dynamicds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMqSender {

    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "risk.sync.exchange";
    private static final String ROUTING_KEY = "risk.sync.completed";

    public void sendSyncCompleted(Long taskId, String dataSourceKey,
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
            log.info("[RabbitMQ] sent taskId={}, dataSourceKey={}, pulled={}, saved={}",
                    taskId, dataSourceKey, totalPulled, totalSaved);
        } catch (Exception e) {
            log.error("[RabbitMQ] failed taskId={}", taskId, e);
        }
    }
}
