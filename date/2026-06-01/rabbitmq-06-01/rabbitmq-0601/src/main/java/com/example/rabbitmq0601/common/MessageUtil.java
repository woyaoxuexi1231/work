package com.example.rabbitmq0601.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一消息体 + 工具方法
 *
 * 每条消息携带：
 * - messageId: 全局唯一，用于幂等去重
 * - traceId:   链路追踪 ID，串联生产→消费全链路
 * - timestamp: 消息创建时间
 * - payload:   业务数据
 */
public class MessageUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 生成一个带 messageId + traceId 的消息 Map
     */
    public static Map<String, Object> buildMessage(String payloadJson) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("messageId", UUID.randomUUID().toString());
        msg.put("traceId",  UUID.randomUUID().toString().substring(0, 8));
        msg.put("timestamp", Instant.now().toString());
        msg.put("payload", payloadJson);
        return msg;
    }

    /**
     * 将消息解析为统一的 EventMessage 对象
     */
    public static EventMessage parseEventMessage(Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            return MAPPER.readValue(json, EventMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("消息反序列化失败", e);
        }
    }

    /**
     * 统一事件消息结构
     */
    public static class EventMessage {
        public String messageId;
        public String traceId;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        public Instant timestamp;
        public Object payload;

        public EventMessage() {}

        @Override
        public String toString() {
            return String.format("[msgId=%s, traceId=%s, payload=%s]", messageId, traceId, payload);
        }
    }
}
