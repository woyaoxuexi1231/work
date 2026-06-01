package com.example.rabbitmq0601.q3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Q3: 镜像队列的主从同步与数据安全性
 *
 * 实战类型：操作文档为主 + 代码演示辅助
 *
 * 演示场景：发布消息到镜像队列，观察 publisher confirm 时机。
 * 正常时 confirm 在毫秒级返回；杀 master 后 confirm 超时/拒绝。
 * 这个 Controller 把 confirm 的"成功、超时、失败"三种状态可视化。
 *
 * 巧妙之处：设置一个 2 秒的超时来模拟 "master 宕机 → confirm 无法返回"，
 * 让你亲眼看到：在镜像队列中，confirm 回来才算消息安全落地。
 * 如果 confirm 超时，消息可能未复制到 mirror，生产者必须重发。
 */
@RestController
@RequestMapping("/q3")
public class Q3Controller {

    private static final Logger log = LoggerFactory.getLogger(Q3Controller.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    // 记录 confirm 结果
    private final Map<String, ConfirmRecord> confirmRecords = new ConcurrentHashMap<>();

    public Q3Controller(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;

        // 【重点】注册 ConfirmCallback —— 每条消息到达 Broker 后回调
        rabbitTemplate.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            String msgId = correlationData != null ? correlationData.getId() : "unknown";
            ConfirmRecord record = confirmRecords.computeIfAbsent(msgId, k -> new ConfirmRecord());
            record.msgId = msgId;
            record.confirmed = true;
            record.ack = ack;
            record.cause = cause;
            record.confirmTime = new Date();

            log.info("Confirm回调: msgId={}, ack={}, cause={}", msgId, ack, cause);
        });
    }

    /**
     * 【对比实验 - 正面】正常发布：confirm 快速返回
     *
     * 调用前请确保镜像队列策略已配置 (运行 02-mirrored-queue.sh)
     */
    @GetMapping("/publish-confirm-ok")
    public Map<String, Object> publishAndConfirm(@RequestParam(defaultValue = "10") int count) {
        List<ConfirmRecord> results = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            String msgId = UUID.randomUUID().toString();
            CorrelationData cd = new CorrelationData(msgId);

            long start = System.nanoTime();
            // 【重点】发布到镜像队列 mirrored.order.pending
            rabbitTemplate.convertAndSend("", "mirrored.order.pending",
                    Map.of("testId", i, "msgId", msgId), cd);

            // 【重点】等待 confirm（最多 5 秒）
            try {
                boolean ok = cd.getFuture().get(5, TimeUnit.SECONDS);
                long elapsed = System.nanoTime() - start;

                ConfirmRecord r = new ConfirmRecord();
                r.msgId = msgId;
                r.confirmed = ok;
                r.latencyMs = TimeUnit.NANOSECONDS.toMillis(elapsed);
                r.result = ok ? "✓ CONFIRMED" : "✗ NACKED";
                results.add(r);
            } catch (Exception e) {
                ConfirmRecord r = new ConfirmRecord();
                r.msgId = msgId;
                r.confirmed = false;
                r.result = "✗ TIMEOUT — 消息可能未复制到 mirror，需重发!";
                r.error = e.getMessage();
                results.add(r);
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("queue", "mirrored.order.pending");
        resp.put("total", count);
        resp.put("results", results);

        long confirmed = results.stream().filter(r -> r.confirmed).count();
        resp.put("confirmed", confirmed);
        resp.put("verdict", confirmed == count
                ? "✓ 全部 confirm! 消息已安全写入 master + mirror → 面试题中说的'confirm 回来表示消息已在所有 mirror 上落盘'"
                : String.format("✗ %d 条未确认 — 可能是 mirror 同步超时或集群异常", count - confirmed));

        return resp;
    }

    /**
     * 对比：发布到普通队列（非镜像）—— confirm 只等 master 一个节点
     */
    @GetMapping("/publish-classic")
    public Map<String, Object> publishToClassic() {
        // 先声明普通队列
        rabbitAdmin.declareQueue(
                new org.springframework.amqp.core.Queue("q3.normal.test", true, false, false));

        long start = System.nanoTime();
        String msgId = UUID.randomUUID().toString();
        CorrelationData cd = new CorrelationData(msgId);

        rabbitTemplate.convertAndSend("", "q3.normal.test",
                Map.of("test", "classic"), cd);

        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            cd.getFuture().get(5, TimeUnit.SECONDS);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            resp.put("queue", "q3.normal.test (普通队列)");
            resp.put("confirm_latency_ms", elapsed);
            resp.put("verdict", String.format(
                    "普通队列 confirm 只需 %dms —— 只需 master 一个节点确认。"
                    + "对比镜像队列的 confirm，它不需要等 mirror，所以更快。"
                    + "但代价是：master 宕机 → 消息完蛋。这就是面试题 Q3 的核心权衡。", elapsed));
        } catch (Exception e) {
            resp.put("error", e.getMessage());
        }

        return resp;
    }

    static class ConfirmRecord {
        String msgId;
        boolean confirmed;
        boolean ack;
        String cause;
        String result;
        String error;
        long latencyMs;
        Date confirmTime;

        @Override
        public String toString() {
            return String.format("[%s] %s latency=%dms",
                    msgId.substring(0, 8), result, latencyMs);
        }
    }
}
