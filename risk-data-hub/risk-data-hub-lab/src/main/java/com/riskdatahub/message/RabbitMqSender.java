package com.riskdatahub.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RabbitMQ 消息发送器 — 同步任务完成后发送完成通知。
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMqSender {

    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "risk.sync.exchange";
    private static final String ROUTING_KEY = "risk.sync.completed";

    /**
     * 发送同步完成消息到 RabbitMQ。
     *
     * @param taskId         同步任务 ID
     * @param dataSourceKey  数据源标识
     * @param datasourceType 数据源类型
     * @param totalPulled    总拉取数
     * @param totalSaved     总落库数
     */
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
