package com.example.rabbitmq0601.q7;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Q7: 统一可靠事件总线接口
 *
 * 设计目标：屏蔽镜像队列/仲裁队列的差异，提供统一的可靠消息收发能力。
 *
 * 核心能力：
 *  - 发送端：异步确认 + 失败重试（指数退避） + 本地发件箱兜底
 *  - 消费端：幂等去重 + 手动 ack + 死信重试 + 告警
 *  - 连接容灾：自动重连 + 拓扑恢复
 *  - 可观测性：traceId 全链路串联
 */
public interface EventBus {

    /**
     * 发布事件 —— 返回 CompletableFuture，调用方可选择同步等待或异步处理
     *
     * @param eventType 事件类型（用于路由到对应队列）
     * @param payload   业务数据
     * @return 包含 messageId 的 Future，confirm 成功后完成
     */
    CompletableFuture<String> publish(String eventType, Object payload);

    /**
     * 订阅事件
     *
     * @param eventType 事件类型（对应队列名）
     * @param handler   消费处理逻辑
     */
    void subscribe(String eventType, EventHandler handler);

    /**
     * 事件处理器接口
     */
    @FunctionalInterface
    interface EventHandler {
        /**
         * 处理事件
         * @param event 事件消息（包含 messageId, traceId, payload）
         * @throws Exception 处理失败则触发重试/死信
         */
        void handle(Event event) throws Exception;
    }

    /**
     * 统一事件结构
     */
    class Event {
        public String messageId;
        public String traceId;
        public String eventType;
        public Object payload;
        public Map<String, Object> headers;

        @Override
        public String toString() {
            return String.format("Event[msgId=%s, traceId=%s, type=%s, payload=%s]",
                    messageId, traceId, eventType, payload);
        }
    }
}
