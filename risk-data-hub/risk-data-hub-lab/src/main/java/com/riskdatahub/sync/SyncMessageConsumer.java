package com.riskdatahub.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 同步消息消费者 — 模拟下游系统接收同步完成通知。
 * <p>仅作日志记录，不参与实际业务逻辑。</p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Component
public class SyncMessageConsumer {

    private static final String EXCHANGE = "risk.sync.exchange";
    private static final String QUEUE = "risk.sync.completed.queue";
    private static final String ROUTING_KEY = "risk.sync.completed";

    @RabbitListener(queues = QUEUE)
    public void handleSyncCompleted(Map<String, Object> message) {
        log.info("[SyncConsumer] 收到同步完成消息 ✅ content={}", message);
    }

    @Configuration
    static class QueueConfig {

        @Bean
        public Queue syncCompletedQueue() {
            return new Queue(QUEUE, true);
        }

        @Bean
        public TopicExchange syncExchange() {
            return new TopicExchange(EXCHANGE);
        }

        @Bean
        public Binding syncCompletedBinding(Queue syncCompletedQueue, TopicExchange syncExchange) {
            return BindingBuilder.bind(syncCompletedQueue).to(syncExchange).with(ROUTING_KEY);
        }
    }
}
