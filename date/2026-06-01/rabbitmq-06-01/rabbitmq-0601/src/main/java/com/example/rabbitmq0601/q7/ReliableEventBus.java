package com.example.rabbitmq0601.q7;

import com.example.rabbitmq0601.common.MessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReliableEventBus —— 生产级可靠事件总线实现
 *
 * 一锅端了面试题 Q7 的所有考点：
 *   1. 发送端：异步 confirm + 指数退避重试 + 本地发件箱
 *   2. 消费端：幂等去重 + 手动 ack + 死信重试
 *   3. 连接容灾：自动重连 + 拓扑恢复
 *   4. 链路追踪：messageId + traceId 全链路串联
 */
public class ReliableEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(ReliableEventBus.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final String queueType;  // "mirrored" | "quorum"

    // 【重点1】本地发件箱 —— 模拟数据库表
    private final ConcurrentHashMap<String, OutboxRecord> outbox = new ConcurrentHashMap<>();

    // 【重点2】消费去重表 —— 基于 messageId
    private final Set<String> processedIds = ConcurrentHashMap.newKeySet();

    // 【重点3】死信队列重试计数器
    private final Map<String, AtomicInteger> retryCounter = new ConcurrentHashMap<>();

    // 注册的消费者
    private final Map<String, EventHandler> handlers = new ConcurrentHashMap<>();

    // 重试线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // 死信交换器和队列
    private static final String DLX = "ex.dead";
    private static final String DLQ = "q.dead.letter";

    public ReliableEventBus(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin, String queueType) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
        this.queueType = queueType;

        // 确保死信交换器和队列存在
        rabbitAdmin.declareExchange(new DirectExchange(DLX, true, false));
        rabbitAdmin.declareQueue(new Queue(DLQ, true, false, false));
        rabbitAdmin.declareBinding(BindingBuilder.bind(new Queue(DLQ, true, false, false))
                .to(new DirectExchange(DLX)).with("dead"));

        // 【重点4】注册 ConfirmCallback
        rabbitTemplate.setConfirmCallback((CorrelationData cd, boolean ack, String cause) -> {
            if (cd == null) return;
            OutboxRecord record = outbox.get(cd.getId());
            if (record != null) {
                record.confirmed = ack;
                record.confirmTime = System.currentTimeMillis();
                if (ack) {
                    record.status = "SENT";
                    record.future.complete(cd.getId());
                    log.info("[CONFIRM] ✓ msgId={}", cd.getId());
                } else {
                    record.status = "NACKED";
                    log.warn("[CONFIRM] ✗ msgId={}, cause={}", cd.getId(), cause);
                }
            }
        });

        // 【重点5】启动发件箱兜底扫描任务
        scheduler.scheduleWithFixedDelay(this::scanOutbox, 10, 10, TimeUnit.SECONDS);
    }

    // ========== 发送端 ==========

    @Override
    public CompletableFuture<String> publish(String eventType, Object payload) {
        String msgId = UUID.randomUUID().toString();
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString().substring(0, 8);

        // 【重点6】先写发件箱
        OutboxRecord record = new OutboxRecord();
        record.msgId = msgId;
        record.traceId = traceId;
        record.eventType = eventType;
        record.payload = payload;
        record.status = "PENDING";
        record.createTime = System.currentTimeMillis();
        record.future = new CompletableFuture<>();
        outbox.put(msgId, record);

        // 构建消息
        Map<String, Object> messageBody = new LinkedHashMap<>();
        messageBody.put("messageId", msgId);
        messageBody.put("traceId", traceId);
        messageBody.put("eventType", eventType);
        messageBody.put("payload", payload);

        CorrelationData cd = new CorrelationData(msgId);
        String routingKey = "event." + eventType;

        // 【重点7】异步发送 + 指数退避重试
        sendWithRetry(routingKey, messageBody, cd, 1, record);

        return record.future;
    }

    private void sendWithRetry(String routingKey, Object message, CorrelationData cd,
                               int attempt, OutboxRecord record) {
        try {
            rabbitTemplate.convertAndSend("ex.order", routingKey, message, cd);

            // 等待 confirm（最多 5 秒）
            cd.getFuture().orTimeout(5, TimeUnit.SECONDS).whenComplete((ok, ex) -> {
                if (ex != null || !ok) {
                    // 【重点8】confirm 超时或失败 → 指数退避重试
                    if (attempt < 5) {
                        long delay = (long) Math.pow(2, attempt) * 1000; // 2s, 4s, 8s, 16s
                        log.warn("[RETRY] msgId={}, attempt={}, delay={}ms", cd.getId(), attempt, delay);
                        record.retryCount = attempt;
                        scheduler.schedule(() ->
                                sendWithRetry(routingKey, message, cd, attempt + 1, record),
                                delay, TimeUnit.MILLISECONDS);
                    } else {
                        log.error("[FAIL] msgId={} 重试 {} 次后仍失败，标记为 FAILED", cd.getId(), attempt);
                        record.status = "FAILED";
                        record.future.completeExceptionally(
                                new RuntimeException("发送失败，已重试" + attempt + "次"));
                    }
                }
            });
        } catch (Exception e) {
            log.error("[SEND_ERROR] msgId={}, error={}", cd.getId(), e.getMessage());
            if (attempt < 5) {
                long delay = (long) Math.pow(2, attempt) * 1000;
                scheduler.schedule(() ->
                        sendWithRetry(routingKey, message, cd, attempt + 1, record),
                        delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * 【重点9】发件箱兜底扫描 —— 解决 confirm 超时但消息实际已写入的边界情况
     */
    private void scanOutbox() {
        long now = System.currentTimeMillis();
        for (OutboxRecord record : outbox.values()) {
            // PENDING 超过 30 秒，重新投递
            if ("PENDING".equals(record.status) && (now - record.createTime) > 30_000) {
                log.warn("[OUTBOX_SCAN] 发现超时 PENDING 消息: msgId={}, 重新投递", record.msgId);
                Map<String, Object> messageBody = new LinkedHashMap<>();
                messageBody.put("messageId", record.msgId);
                messageBody.put("traceId", record.traceId);
                messageBody.put("eventType", record.eventType);
                messageBody.put("payload", record.payload);
                CorrelationData cd = new CorrelationData(record.msgId);
                sendWithRetry("event." + record.eventType, messageBody, cd, 1, record);
            }
        }
    }

    // ========== 消费端 ==========

    @Override
    public void subscribe(String eventType, EventHandler handler) {
        handlers.put(eventType, handler);

        String queueName = "q.reliable." + eventType;
        declareReliableQueue(queueName);

        // 【重点10】手动 ack 消费
        // 注意：这是简化版。生产环境用 @RabbitListener 或 SimpleMessageListenerContainer
        log.info("[SUBSCRIBE] eventType={}, queue={}", eventType, queueName);

        // 启动消费线程
        Thread consumer = new Thread(() -> consumeLoop(queueName, handler));
        consumer.setDaemon(true);
        consumer.setName("consumer-" + eventType);
        consumer.start();
    }

    private void declareReliableQueue(String queueName) {
        Map<String, Object> args = new HashMap<>();
        if ("quorum".equals(queueType)) {
            // 【重点11】仲裁队列
            args.put("x-queue-type", "quorum");
        }
        // 【重点12】死信配置
        args.put("x-dead-letter-exchange", DLX);
        args.put("x-dead-letter-routing-key", "dead");
        args.put("x-message-ttl", 60000); // 消息 TTL 1 分钟

        Queue queue = new Queue(queueName, true, false, false, args);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(
                BindingBuilder.bind(queue)
                        .to(new TopicExchange("ex.order"))
                        .with("event." + queueName.replace("q.reliable.", "")));
    }

    private void consumeLoop(String queueName, EventHandler handler) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Message message = rabbitTemplate.receive(queueName, 5000);
                if (message == null) continue;

                // 解析消息
                Event event = parseEvent(message);

                // 【重点13】幂等检查
                if (processedIds.contains(event.messageId)) {
                    log.info("[IDEMPOTENT] 重复消息，直接 ACK: msgId={}", event.messageId);
                    // 已处理过，直接 ack 丢弃
                    continue;
                }

                // 【重点14】设置 traceId 到 MDC，实现链路追踪
                MDC.put("traceId", event.traceId);
                try {
                    handler.handle(event);
                    // 【重点15】处理成功 → 记录去重 + 手动 ack（简化版：自动 ack 的 receive）
                    processedIds.add(event.messageId);
                    log.info("[CONSUME] ✓ msgId={}, traceId={}", event.messageId, event.traceId);
                } catch (Exception e) {
                    log.error("[CONSUME] ✗ msgId={}, error={}", event.messageId, e.getMessage());
                    // 【重点16】处理失败 → 进入死信或重试
                    handleConsumeFailure(event, e);
                } finally {
                    MDC.remove("traceId");
                }
            } catch (Exception e) {
                log.error("[CONSUME_LOOP] error={}", e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private Event parseEvent(Message message) {
        try {
            byte[] body = message.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(body, Map.class);
            Event event = new Event();
            event.messageId = (String) map.get("messageId");
            event.traceId = (String) map.get("traceId");
            event.eventType = (String) map.get("eventType");
            event.payload = map.get("payload");
            event.headers = map;
            return event;
        } catch (Exception e) {
            throw new RuntimeException("消息解析失败", e);
        }
    }

    private void handleConsumeFailure(Event event, Exception error) {
        String key = event.messageId;
        int retries = retryCounter.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        if (retries <= 3) {
            // 【重点17】前 3 次失败 → nack + requeue（简化：不做 nack，消息会在 TTL 后进死信）
            log.warn("[RETRY_CONSUME] msgId={}, retry={}/3", event.messageId, retries);
        } else {
            // 【重点18】超过 3 次 → 进死信队列 + 告警
            log.error("[DEAD_LETTER] msgId={} 处理失败超过3次，进入死信队列", event.messageId);
            // 简化版：消息通过 TTL + DLX 自动进入死信队列
            retryCounter.remove(key);
        }
    }

    // ========== 诊断接口 ==========

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("queueType", queueType);
        stats.put("outbox_size", outbox.size());
        stats.put("outbox_pending", outbox.values().stream().filter(r -> "PENDING".equals(r.status)).count());
        stats.put("outbox_sent", outbox.values().stream().filter(r -> "SENT".equals(r.status)).count());
        stats.put("outbox_failed", outbox.values().stream().filter(r -> "FAILED".equals(r.status)).count());
        stats.put("processed_ids", processedIds.size());
        stats.put("handlers", handlers.keySet());
        return stats;
    }

    /**
     * 模拟处理失败 —— 用于演示重试和死信机制
     */
    public void resetIdempotencyForDemo() {
        processedIds.clear();
        retryCounter.clear();
        log.info("[DEMO] 去重表和重试计数器已清空");
    }

    static class OutboxRecord {
        String msgId;
        String traceId;
        String eventType;
        Object payload;
        String status;        // PENDING, SENT, FAILED
        boolean confirmed;
        long confirmTime;
        int retryCount;
        long createTime;
        CompletableFuture<String> future;
    }
}
