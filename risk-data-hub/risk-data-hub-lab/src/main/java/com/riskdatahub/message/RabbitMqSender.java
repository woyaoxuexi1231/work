package com.riskdatahub.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * RabbitMQ 消息发送器 — 通用消息发送。
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMqSender {

    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "risk.sync.exchange";

    /**
     * 发送消息到指定 routing key。
     *
     * @param routingKey 路由键
     * @param message    消息内容
     */
    public void sendMessage(String routingKey, Map<String, Object> message) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, message);
            log.info("[RabbitMQ] sent routingKey={}, message={}", routingKey, message);
        } catch (Exception e) {
            log.error("[RabbitMQ] send failed routingKey={}", routingKey, e);
        }
    }
}
