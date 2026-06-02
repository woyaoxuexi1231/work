package com.example.rabbitmq0601.q5;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Q5: 镜像队列与仲裁队列的对比及演进
 *
 * 实战类型：代码演示
 *
 * 演示场景：同一集群中并存两条队列 ——
 *   q5.mirrored.compare → GM 原子广播（镜像，需 ha-policy）
 *   q5.quorum.compare   → Raft 共识（仲裁，x-queue-type=quorum）
 *
 * 对比实验：
 *   反面：镜像队列 —— 延迟低但不稳定，故障有丢数据窗口
 *   正面：仲裁队列 —— 延迟略高但稳定，已确认绝不丢
 *   额外：双写迁移方案
 */
@RestController
@RequestMapping("/q5")
public class Q5Controller {

    private static final Logger log = LoggerFactory.getLogger(Q5Controller.class);
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    public Q5Controller(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
    }

    // ==================== 步骤1: 创建两种队列 ====================

    @GetMapping("/setup")
    public Map<String, Object> setup() {
        rabbitAdmin.deleteQueue("q5.mirrored.compare");
        rabbitAdmin.deleteQueue("q5.quorum.compare");

        // 【重点1】镜像队列 —— 名称匹配 mirrored-all 策略 → 自动获得 exactly:2
        rabbitAdmin.declareQueue(new Queue("q5.mirrored.compare", true, false, false));

        // 【重点2】仲裁队列 —— x-queue-type: quorum 一行参数，底层切到 Raft
        Map<String, Object> quorumArgs = new HashMap<>();
        quorumArgs.put("x-queue-type", "quorum");
        rabbitAdmin.declareQueue(new Queue("q5.quorum.compare", true, false, false, quorumArgs));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("q5.mirrored.compare", "镜像队列 — GM 原子广播，需 mirrored-all 策略已配（02-mirrored-queue.sh）");
        resp.put("q5.quorum.compare", "仲裁队列 — Raft 共识，x-queue-type=quorum，3 节点自动 Raft 组");
        resp.put("next_反面实验", "GET /q5/bench?count=200  ← 双队列并行压测");
        return resp;
    }

    // ==================== 步骤2【反面实验】: 双队列压测 ====================

    @GetMapping("/bench")
    public Map<String, Object> bench(@RequestParam(defaultValue = "200") int count) {
        Map<String, Object> resp = new LinkedHashMap<>();

        // 【重点】同连接、同集群、同消息体 —— 唯一变量：队列类型
        BenchResult rMirrored = measure("q5.mirrored.compare", count);
        BenchResult rQuorum   = measure("q5.quorum.compare",   count);

        resp.put("镜像队列_GM广播", rMirrored.toMap());
        resp.put("仲裁队列_Raft共识", rQuorum.toMap());

        // 对比分析
        Map<String, Object> analysis = new LinkedHashMap<>();

        // 健壮除零保护
        double latencyRatio = rQuorum.avgMs > 0 ? (double) rMirrored.avgMs / rQuorum.avgMs : 1.0;

        analysis.put("延迟比_镜像/仲裁", String.format("%.2fx", latencyRatio));

        if (latencyRatio < 0.85) {
            int pct = (int) Math.round((1.0 - latencyRatio) * 100);
            analysis.put("结论", String.format(
                    "镜像队列平均快 %d%% —— exactly:2 只需等 1 个 mirror，仲裁需多数派(2/3)确认。"
                    + "但镜像的延迟波动大（木桶效应），仲裁的延迟更稳定可预测。", pct));
        } else if (latencyRatio > 1.15) {
            int pct = (int) Math.round((latencyRatio - 1.0) * 100);
            analysis.put("结论", String.format(
                    "仲裁队列更快 %d%% —— Raft 管道批量提交在某些场景下优于 GM 逐个广播。"
                    + "但关键差异不在性能，在故障时的数据安全性。", pct));
        } else {
            analysis.put("结论", "两者延迟接近。真正的差异在故障切换时的数据安全性 —— 见 /q5/verify-after-failover");
        }
        resp.put("分析", analysis);
        resp.put("next_正面对比", "GET /q5/verify-after-failover  ← 故障后数据一致性");
        return resp;
    }

    private BenchResult measure(String queueName, int count) {
        long totalNs = 0;
        long maxNs = 0;

        for (int i = 1; i <= count; i++) {
            long start = System.nanoTime();
            rabbitTemplate.convertAndSend("", queueName, "Q5-BENCH-" + i);
            long elapsed = System.nanoTime() - start;
            totalNs += elapsed;
            if (elapsed > maxNs) maxNs = elapsed;
        }

        BenchResult r = new BenchResult();
        r.count = count;
        r.totalMs = TimeUnit.NANOSECONDS.toMillis(totalNs);
        r.avgMs   = totalNs > 0 ? TimeUnit.NANOSECONDS.toMillis(totalNs) / count : 0;
        r.p99     = TimeUnit.NANOSECONDS.toMillis(maxNs);
        return r;
    }

    // ==================== 步骤3: 故障后数据一致性验证 ====================

    @GetMapping("/verify-after-failover")
    public Map<String, Object> verifyAfterFailover() {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<Map<String, String>> steps = new ArrayList<>();

        steps.add(stepMap("1", "先发消息",
                "curl \"http://localhost:8080/q5/bench?count=100\""));
        steps.add(stepMap("2", "查看 leader/master",
                "docker exec rabbitmq-node1 rabbitmqctl list_queues name leader type --formatter=table 2>&1 | grep q5"));
        steps.add(stepMap("3", "杀掉 master/leader",
                "docker stop rabbitmq-node1"));
        steps.add(stepMap("4", "验证镜像队列",
                "docker exec rabbitmq-node2 rabbitmqadmin -u admin -p admin123 get queue=q5.mirrored.compare ackmode=ack_requeue_false"));
        steps.add(stepMap("5", "验证仲裁队列",
                "docker exec rabbitmq-node2 rabbitmqadmin -u admin -p admin123 get queue=q5.quorum.compare ackmode=ack_requeue_false"));
        steps.add(stepMap("★", "预期结果",
                "镜像队列：可能取到消息，也可能短暂不可用（选举窗口），已确认未复制的消息可能丢失。\n"
                + "仲裁队列：全部可取，Raft 保证新 Leader 拥有所有已 commit 条目 —— 不丢、不脏。"));

        resp.put("操作步骤", steps);
        resp.put("面试答案对应", "\"镜像队列故障切换可能丢已确认消息；仲裁队列基于 Raft，一旦 confirm 回来，消息已在多数节点上。\"");
        return resp;
    }

    // ==================== 步骤4: 迁移方案 ====================

    @GetMapping("/migration-demo")
    public Map<String, Object> migrationDemo() {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<String> flow = new ArrayList<>();

        flow.add("第1步: 创建目标仲裁队列（如 quorum.order.v2）");
        flow.add("第2步: 生产者改为双写两条队列（旧镜像 + 新仲裁），同一条消息带相同 messageId");
        flow.add("第3步: 消费者暂时只消费旧队列");
        flow.add("第4步: 观察仲裁队列运行正常后，消费者逐步切换到仲裁队列");
        flow.add("第5步: 旧队列积压清零 → 停止生产者双写 → 删除旧镜像队列");
        flow.add("");
        flow.add("关键保障: messageId 全局唯一，消费者幂等，无论从哪个队列消费都不会重复处理");

        resp.put("双写迁移步骤", flow);
        resp.put("零停机", "整个过程中生产者不停、消费者不中断");
        return resp;
    }

    @GetMapping("/cleanup")
    public Map<String, Object> cleanup() {
        rabbitAdmin.deleteQueue("q5.mirrored.compare");
        rabbitAdmin.deleteQueue("q5.quorum.compare");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "已清理");
        return result;
    }

    // ==================== 工具 ====================

    private Map<String, String> stepMap(String step, String title, String detail) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("step", step);
        m.put("title", title);
        m.put("detail", detail);
        return m;
    }

    static class BenchResult {
        int count;
        long totalMs;
        long avgMs;
        long p99;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("发送条数", count);
            m.put("总耗时_ms", totalMs);
            m.put("平均_ms", avgMs);
            m.put("P99_ms", p99);
            return m;
        }
    }
}
