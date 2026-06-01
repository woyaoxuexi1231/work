package com.example.rabbitmq0601.q3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Q3: 镜像队列的主从同步与数据安全性
 *
 * 实战类型：操作文档 + 代码演示（混合）
 *
 * 操作部分：运行 02-mirrored-queue.sh，配置镜像策略，杀 master 观察故障切换
 * 代码部分：通过 ConfirmCallback 精确测量镜像队列的 confirm 延迟，
 *          并与普通队列对比 —— 多出来的时间就是 GM 环广播 + mirror 写盘
 */
@RestController
@RequestMapping("/q3")
public class Q3Controller {

    private static final Logger log = LoggerFactory.getLogger(Q3Controller.class);
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    // 记录每条消息的 confirm 结果
    private final Map<String, ConfirmRecord> confirmLog = new ConcurrentHashMap<>();

    public Q3Controller(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;

        // 【重点】注册全局 ConfirmCallback —— 每条消息到达 Broker 后触发
        // ack=true:  消息已写入 master，且所有 mirror 已确认（取决于 ha-mode 配置）
        // ack=false: 消息未成功（队列不存在、镜像不足等）
        rabbitTemplate.setConfirmCallback((CorrelationData cd, boolean ack, String cause) -> {
            if (cd == null) return;
            ConfirmRecord r = confirmLog.computeIfAbsent(cd.getId(), k -> new ConfirmRecord());
            r.msgId = cd.getId().substring(0, 8);
            r.ack = ack;
            r.cause = cause;
            r.confirmTime = System.currentTimeMillis();
            log.info("[CONFIRM] msgId={}, ack={}, cause={}", r.msgId, ack, cause);
        });
    }

    // ==================== 实验 A：镜像队列 confirm 延迟 ====================

    /**
     * 向镜像队列发 20 条消息，精确测量每条 confirm 的延迟
     *
     * 前置：必须已运行 02-mirrored-queue.sh（配置镜像策略 + 创建 mirrored.order.pending）
     */
    @GetMapping("/publish-mirrored")
    public Map<String, Object> publishToMirrored(@RequestParam(defaultValue = "20") int count) {
        confirmLog.clear();
        List<Map<String, Object>> results = new ArrayList<>();
        long totalLatency = 0;
        long maxLatency = 0;

        for (int i = 1; i <= count; i++) {
            String msgId = UUID.randomUUID().toString();
            CorrelationData cd = new CorrelationData(msgId);

            long start = System.nanoTime();
            // 【重点】发到镜像队列 —— confirm 必须等 mirror 也写入后才返回
            rabbitTemplate.convertAndSend("", "mirrored.order.pending",
                    toPayload(i, msgId.substring(0, 8)), cd);

            try {
                // 【重点】同步等待 confirm，最大 5 秒
                cd.getFuture().get(5, TimeUnit.SECONDS);
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                totalLatency += elapsedMs;
                if (elapsedMs > maxLatency) maxLatency = elapsedMs;

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("seq", i);
                r.put("latency_ms", elapsedMs);
                r.put("status", "✓ CONFIRMED");
                results.add(r);
            } catch (Exception e) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("seq", i);
                r.put("status", "✗ TIMEOUT: " + e.getMessage());
                results.add(r);
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("queue", "mirrored.order.pending (镜像队列, exactly:2)");
        resp.put("发送条数", count);
        resp.put("平均延迟_ms", count > 0 ? totalLatency / count : 0);
        resp.put("最大延迟_ms", maxLatency);
        resp.put("明细", results);
        resp.put("关键解读", "每条 confirm 的延迟包含了: master 写盘 → GM 环广播 → mirror 写盘 → mirror 确认 → master 汇总 → 回调。这就是面试题中说的'confirm 回来才代表消息安全落地'。");
        resp.put("next_对比", "GET /q3/publish-classic  ← 普通队列 confirm，对比差异");
        return resp;
    }

    // ==================== 实验 B：普通队列 confirm（对比基准） ====================

    /**
     * 向普通队列发 20 条 —— confirm 只需 master 一个节点确认
     */
    @GetMapping("/publish-classic")
    public Map<String, Object> publishToClassic(@RequestParam(defaultValue = "20") int count) {
        confirmLog.clear();

        // 确保普通队列存在
        rabbitAdmin.declareQueue(new Queue("q3.classic.control", true, false, false));

        List<Map<String, Object>> results = new ArrayList<>();
        long totalLatency = 0;
        long maxLatency = 0;

        for (int i = 1; i <= count; i++) {
            String msgId = UUID.randomUUID().toString();
            CorrelationData cd = new CorrelationData(msgId);

            long start = System.nanoTime();
            // 【重点】发到普通队列 —— confirm 只等 master
            rabbitTemplate.convertAndSend("", "q3.classic.control",
                    toPayload(i, ""), cd);

            try {
                cd.getFuture().get(5, TimeUnit.SECONDS);
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                totalLatency += elapsedMs;
                if (elapsedMs > maxLatency) maxLatency = elapsedMs;

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("seq", i);
                r.put("latency_ms", elapsedMs);
                r.put("status", "✓ CONFIRMED");
                results.add(r);
            } catch (Exception e) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("seq", i);
                r.put("status", "✗ FAILED");
                results.add(r);
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("queue", "q3.classic.control (普通队列，无镜像)");
        resp.put("发送条数", count);
        resp.put("平均延迟_ms", count > 0 ? totalLatency / count : 0);
        resp.put("最大延迟_ms", maxLatency);
        resp.put("明细", results);
        resp.put("关键解读", "普通队列的 confirm 只需 master 一个节点确认 —— 更快，但 master 宕机后消息锁死在磁盘上。");
        resp.put("compare_with", "请对比 /q3/publish-mirrored 的延迟，差值 = GM 环广播 + mirror 写盘的时间");
        return resp;
    }

    // ==================== 实验 C：故障期间的 confirm 行为 ====================

    /**
     * 在杀 master 期间发消息，观察 confirm 行为
     *
     * 操作步骤：
     *   1. 先确认 mirrored.order.pending 的 master 节点
     *   2. 执行 docker stop <master节点>
     *   3. 立即调用本接口
     *   4. 观察 confirm 状态：TIMEOUT → 重试 → 新 master 确认
     */
    @GetMapping("/confirm-during-failover")
    public Map<String, Object> confirmDuringFailover() {
        List<Map<String, Object>> log = new ArrayList<>();

        log.add(stepMap(1, "说明", "确认镜像队列 master 节点"));
        log.add(stepMap(2, "命令",
                "docker exec rabbitmq-node1 rabbitmqctl list_queues name node | grep mirrored"));
        log.add(stepMap(3, "命令",
                "docker stop <master节点>    # 杀掉 master"));
        log.add(stepMap(4, "命令",
                "curl http://localhost:8080/q3/publish-mirrored?count=5    # 立即发消息"));
        log.add(stepMap(5, "观察",
                "部分消息 confirm 状态为 TIMEOUT —— master 宕机后 mirror 正在选举，confirm 无法返回"));
        log.add(stepMap(6, "关键",
                "TIMEOUT 的消息可能实际已被旧 master 写入但未复制到 mirror —— 这就是镜像队列'已确认仍可能丢'的窗口。"));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("操作流程", log);
        resp.put("面试答案对应", "\"master 宕机前已确认但未复制到 slave 的消息会丢失\"——你刚发的 TIMEOUT 消息就在这个窗口里。");
        return resp;
    }

    // ==================== 工具 ====================

    private Map<String, Object> toPayload(int testId, String msgId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("testId", testId);
        if (msgId != null && !msgId.isEmpty()) m.put("msgId", msgId);
        return m;
    }

    private Map<String, Object> stepMap(int step, String key, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step", step);
        m.put(key, value);
        return m;
    }

    @GetMapping("/cleanup")
    public Map<String, Object> cleanup() {
        rabbitAdmin.deleteQueue("q3.classic.control");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "已清理");
        return result;
    }

    static class ConfirmRecord {
        String msgId;
        boolean ack;
        String cause;
        long confirmTime;
    }
}
