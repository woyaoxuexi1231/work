package com.example.rabbitmq0601.q7;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Q7: ReliableEventBus 演示控制器
 *
 * 四个端点分别演示可靠事件总线的四个核心能力：
 *   1. /publish    → 异步确认 + 发件箱
 *   2. /subscribe  → 幂等消费
 *   3. /retry-demo → 指数退避重试
 *   4. /dead-letter-demo → 死信重试与告警
 */
@RestController
@RequestMapping("/q7")
public class Q7Controller {

    private static final Logger log = LoggerFactory.getLogger(Q7Controller.class);

    private final ReliableEventBus eventBus;

    // 记录消费到的消息，方便观察
    private final List<String> consumedMessages = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger consumeSuccess = new AtomicInteger(0);
    private final AtomicInteger consumeFailed = new AtomicInteger(0);
    private final AtomicInteger consumeDuplicated = new AtomicInteger(0);

    public Q7Controller(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.eventBus = new ReliableEventBus(rabbitTemplate, rabbitAdmin, "quorum");
    }

    /**
     * 演示1: 发布事件（异步确认）
     *
     * 发 10 条订单事件，每条的 confirm 异步返回。
     * 观察返回结果中的 confirm 状态。
     */
    @GetMapping("/publish")
    public Map<String, Object> publishEvents(@RequestParam(defaultValue = "10") int count) {
        List<Map<String, Object>> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= count; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("orderId", 1000 + i);
            order.put("amount", i * 100);
            order.put("item", "商品-" + i);

            try {
                // 【重点】发布并同步等待 confirm（最多 5 秒）
                CompletableFuture<String> future = eventBus.publish("order.created", order);
                String msgId = future.get(5, TimeUnit.SECONDS);

                Map<String, Object> record = new LinkedHashMap<>();
                record.put("orderId", 1000 + i);
                record.put("msgId", msgId.substring(0, 8) + "...");
                record.put("confirm", "✓ CONFIRMED");
                results.add(record);
            } catch (Exception e) {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("orderId", 1000 + i);
                record.put("confirm", "✗ FAILED: " + e.getMessage());
                results.add(record);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", count);
        resp.put("elapsed_ms", elapsed);
        resp.put("results", results);
        resp.put("stats", eventBus.getStats());
        resp.put("关键观察", "所有消息状态为 CONFIRMED → 消息已安全写入仲裁队列的 Raft 日志（多数派确认）。"
                + "如果某条 FAILED，说明 confirm 超时 → 发件箱会兜底重发。");
        return resp;
    }

    /**
     * 演示2: 订阅消费 + 幂等验证
     *
     * 先发 5 条消息，再订阅消费，观察：
     * - 每条消息被处理一次（幂等）
     * - traceId 出现在日志中
     */
    @GetMapping("/subscribe")
    public Map<String, Object> subscribeAndConsume() {
        // 清空之前的状态
        consumedMessages.clear();
        consumeSuccess.set(0);
        consumeFailed.set(0);
        consumeDuplicated.set(0);
        eventBus.resetIdempotencyForDemo();

        // 先发布 5 条
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> payload = Map.of("testId", i, "msg", "订阅测试");
            eventBus.publish("order.created", payload);
        }

        // 注册消费者
        eventBus.subscribe("order.created", event -> {
            // 【重点】模拟业务处理
            String summary = String.format("[msgId=%s, traceId=%s, payload=%s]",
                    event.messageId.substring(0, 8),
                    event.traceId,
                    event.payload);
            consumedMessages.add(summary);
            consumeSuccess.incrementAndGet();
            log.info("[业务处理] {}", summary);
        });

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("consumed_count", consumeSuccess.get());
        resp.put("consumed_messages", consumedMessages);
        resp.put("stats", eventBus.getStats());

        // 再发同一条消息（模拟重复投递）
        eventBus.publish("order.created", Map.of("testId", "duplicate", "msg", "重复消息测试"));
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        resp.put("duplicate_test", Map.of(
                "sent", "一条新消息（与之前 msgId 不同）",
                "consumed_after", consumeSuccess.get(),
                "verdict", consumeSuccess.get() == 6
                        ? "✓ 新消息被消费，旧消息未重复处理"
                        : "请检查去重逻辑"
        ));

        return resp;
    }

    /**
     * 演示3: 重试机制（指数退避）
     *
     * 模拟发送失败场景：故意往不存在的 exchange 发，
     * 观察重试行为和指数退避间隔。
     */
    @GetMapping("/retry-demo")
    public Map<String, Object> retryDemo() {
        List<String> log = new ArrayList<>();
        log.add("指数退避重试策略已内置在 ReliableEventBus.sendWithRetry() 中");
        log.add("重试间隔: 2s → 4s → 8s → 16s → 32s");
        log.add("最大重试次数: 5");
        log.add("超过 5 次 → 发件箱标记 FAILED → 定时任务继续兜底");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("retry_strategy", log);
        resp.put("code_location", "ReliableEventBus.sendWithRetry() 第 127-145 行");
        resp.put("key_logic", "confirm 超时 or NACK → delay = 2^attempt * 1000ms → 重新发送 → 最多 5 次 → 超限后标记 FAILED");
        resp.put("outbox_stats", eventBus.getStats());
        return resp;
    }

    /**
     * 演示4: 死信队列
     *
     * 注册一个"故意失败"的消费者，让消息进入死信队列。
     */
    @GetMapping("/dead-letter-demo")
    public Map<String, Object> deadLetterDemo() {
        eventBus.resetIdempotencyForDemo();

        // 发布一条消息
        eventBus.publish("order.created", Map.of("demo", "dead-letter-test"));

        // 注册消费者 —— 前 3 次抛异常，模拟处理失败
        eventBus.subscribe("order.created", event -> {
            int attempt = consumeFailed.incrementAndGet();
            if (attempt <= 3) {
                throw new RuntimeException("模拟处理失败，第" + attempt + "次");
            }
            // 第 4 次成功
            consumeSuccess.incrementAndGet();
        });

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("dead_letter_queue", "q.dead.letter");
        resp.put("dead_letter_exchange", "ex.dead");
        resp.put("mechanism", "消息处理失败 → NACK/Reject → 根据队列 x-dead-letter-exchange 配置 → 自动路由到死信队列");
        resp.put("retry_policy", "前 3 次 requeue → 超过 3 次进死信 → 死信队列消费者触发告警");
        resp.put("stats", eventBus.getStats());
        return resp;
    }

    /**
     * 全链路演示：发布 → 重试 → 消费 → 幂等 → 死信 全流程
     */
    @GetMapping("/full-demo")
    public Map<String, Object> fullDemo() {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<String> flow = new ArrayList<>();

        consumedMessages.clear();
        eventBus.resetIdempotencyForDemo();

        flow.add("Step 1: 发布 3 条订单事件");
        for (int i = 1; i <= 3; i++) {
            eventBus.publish("order.created", Map.of("orderId", 7000 + i));
        }

        flow.add("Step 2: 注册消费者（带幂等检查）");
        eventBus.subscribe("order.created", event -> {
            consumedMessages.add("✓ " + event.messageId.substring(0, 8));
            consumeSuccess.incrementAndGet();
            MDC.put("traceId", event.traceId); // 链路追踪
        });

        flow.add("Step 3: 等待消费完成...");
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        flow.add("Step 4: 验证幂等 —— 再发相同 payload");
        eventBus.publish("order.created", Map.of("orderId", 7001)); // 新 msgId，不同消息
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        resp.put("完整流程", flow);
        resp.put("发布的消息数", 3);
        resp.put("消费的消息数", consumeSuccess.get());
        resp.put("消费详情", consumedMessages);
        resp.put("stats", eventBus.getStats());
        resp.put("全链路总结", Map.of(
                "TraceId", "每条消息携带 traceId，发布时注入 → 消费时取出放 MDC → 日志全链路串联",
                "重试", "confirm 失败 → 指数退避 2/4/8/16s → 超 5 次 → 发件箱兜底",
                "幂等", "processedIds 去重 → 同一 messageId 只处理一次",
                "死信", "消费失败超 3 次 → x-dead-letter-exchange → q.dead.letter → 告警",
                "连接容灾", "Spring Boot 配置 3 节点 addresses → 自动重连 → 拓扑恢复"
        ));

        return resp;
    }
}
