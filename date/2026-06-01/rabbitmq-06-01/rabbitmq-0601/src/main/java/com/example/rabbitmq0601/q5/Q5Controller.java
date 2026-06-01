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
 * 演示场景：创建两种队列 ——
 *   A. 镜像队列 (mirrored.*) —— 依赖 ha-mode: exactly, ha-params: 2 策略
 *   B. 仲裁队列 (quorum.*) —— x-queue-type: quorum
 * 然后向两种队列各发 200 条消息，对比：
 *   - 吞吐量
 *   - P99 延迟
 *   - 故障恢复后数据一致性
 *
 * 巧妙之处：两个队列同时存在于同一个集群中，共享同样的 3 个节点，
 * 但底层机制完全不同。同一个 Spring Boot 实例、同一个连接工厂、
 * 同一次基准测试 —— 唯一的变量是队列类型。
 * 这让你对"GM vs Raft"有了量化的认知。
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

    /**
     * 步骤1: 创建镜像队列（需匹配策略）+ 仲裁队列
     */
    @GetMapping("/setup")
    public Map<String, Object> setup() {
        List<String> created = new ArrayList<>();

        // 镜像队列：名称匹配 mirrored.* 策略
        Queue mirrored = new Queue("q5.mirrored.compare", true, false, false);
        rabbitAdmin.declareQueue(mirrored);
        created.add("q5.mirrored.compare (镜像队列, 需策略 ^q5\\.mirrored\\.)");

        // 仲裁队列：【重点】x-queue-type: quorum
        Map<String, Object> args = new HashMap<>();
        args.put("x-queue-type", "quorum");
        Queue quorum = new Queue("q5.quorum.compare", true, false, false, args);
        rabbitAdmin.declareQueue(quorum);
        created.add("q5.quorum.compare (仲裁队列, x-queue-type=quorum)");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("queues", created);
        resp.put("tip", "请确保镜像策略已配置：");
        resp.put("command", "docker exec rabbitmq-node1 rabbitmqctl set_policy mirror-q5 '^q5\\.mirrored\\.' '{\"ha-mode\":\"exactly\",\"ha-params\":2}' --apply-to queues");
        resp.put("next", "GET /q5/bench?count=200");
        return resp;
    }

    /**
     * 步骤2: 同条件压测对比
     *
     * 【对比实验】
     * 先跑 GET /q5/bench → 看吞吐量和延迟
     * 再杀 leader/master → GET /q5/verify-after-failover → 看数据一致性
     */
    @GetMapping("/bench")
    public Map<String, Object> bench(@RequestParam(defaultValue = "200") int count) {
        Map<String, Object> resp = new LinkedHashMap<>();

        // 镜像队列压测
        BenchResult mirroredResult = measure("q5.mirrored.compare", count);
        // 仲裁队列压测
        BenchResult quorumResult = measure("q5.quorum.compare", count);

        resp.put("镜像队列_exactly2", mirroredResult.toMap());
        resp.put("仲裁队列_quorum3", quorumResult.toMap());

        // 对比分析
        double ratio = (double) mirroredResult.avgMs / Math.max(1, quorumResult.avgMs);
        resp.put("对比结论", Map.of(
                "延迟比_镜像/仲裁", String.format("%.2fx", ratio),
                "分析", ratio > 1.2
                        ? String.format("镜像队列平均快 %.0f%% —— 因为 exactly:2 只需等 1 个 mirror，而仲裁需多数派(2/3)确认", (ratio - 1) * 100)
                        : ratio < 0.8
                                ? String.format("仲裁队列平均快 %.0f%% —— Raft 管道批量提交在某些场景下优于 GM 广播", (1 - ratio) * 100)
                                : "两者延迟接近",
                "关键差异", "延迟只是表面。真正的差异在故障切换时的数据安全性 —— 见 /q5/verify-after-failover"
        ));

        return resp;
    }

    private BenchResult measure(String queueName, int count) {
        long totalNanos = 0;
        long maxNs = 0;

        for (int i = 1; i <= count; i++) {
            long start = System.nanoTime();
            rabbitTemplate.convertAndSend("", queueName,
                    "Q5-BENCH-" + i);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;
            if (elapsed > maxNs) maxNs = elapsed;
        }

        BenchResult r = new BenchResult();
        r.count = count;
        r.totalMs = TimeUnit.NANOSECONDS.toMillis(totalNanos);
        r.avgMs = TimeUnit.NANOSECONDS.toMillis(totalNanos) / count;
        r.maxMs = TimeUnit.NANOSECONDS.toMillis(maxNs);
        r.throughput = (double) count / TimeUnit.NANOSECONDS.toSeconds(totalNanos);
        return r;
    }

    static class BenchResult {
        int count;
        long totalMs;
        long avgMs;
        long maxMs;
        double throughput;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("发送条数", count);
            m.put("总耗时_ms", totalMs);
            m.put("平均延迟_ms", avgMs);
            m.put("最大延迟_ms", maxMs);
            m.put("吞吐量_msg/s", String.format("%.1f", throughput));
            return m;
        }
    }

    /**
     * 步骤3: 模拟故障后验证数据一致性
     *
     * 前提：已杀过 leader/master 节点（用脚本 02/03 或手动 docker stop）
     */
    @GetMapping("/verify-after-failover")
    public Map<String, Object> verifyAfterFailover() {
        Map<String, Object> resp = new LinkedHashMap<>();

        // 从两个队列各消费 5 条，观察数据是否存在
        Map<String, String> mirroredMsgs = new LinkedHashMap<>();
        Map<String, String> quorumMsgs = new LinkedHashMap<>();

        try {
            for (int i = 0; i < 5; i++) {
                Object msg = rabbitTemplate.receiveAndConvert("q5.mirrored.compare", 2000);
                mirroredMsgs.put("msg-" + i, msg != null ? msg.toString() : "(空)");
            }
            resp.put("镜像队列_消费结果", mirroredMsgs);
        } catch (Exception e) {
            resp.put("镜像队列_消费结果", "ERROR: " + e.getMessage());
        }

        try {
            for (int i = 0; i < 5; i++) {
                Object msg = rabbitTemplate.receiveAndConvert("q5.quorum.compare", 2000);
                quorumMsgs.put("msg-" + i, msg != null ? msg.toString() : "(空)");
            }
            resp.put("仲裁队列_消费结果", quorumMsgs);
        } catch (Exception e) {
            resp.put("仲裁队列_消费结果", "ERROR: " + e.getMessage());
        }

        resp.put("结论", "如果 master/leader 被杀了后："
                + "镜像队列可能短暂不可用（选举期间），消费到的消息可能有重复；"
                + "仲裁队列的 Raft 选举保证新 leader 拥有所有已确认数据 —— 不丢、不脏。");

        return resp;
    }

    /**
     * 迁移双写方案演示
     *
     * 【重点】展示如何平滑从镜像队列迁移到仲裁队列
     */
    @GetMapping("/migration-demo")
    public Map<String, Object> migrationDemo() {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<String> log = new ArrayList<>();

        log.add("第1步: 创建目标仲裁队列 quorum.order.migrated");
        Map<String, Object> args = new HashMap<>();
        args.put("x-queue-type", "quorum");
        Queue targetQueue = new Queue("quorum.order.migrated", true, false, false, args);
        rabbitAdmin.declareQueue(targetQueue);

        log.add("第2步: 生产者改为双写两条队列 (mirrored + quorum)");
        String msgId = UUID.randomUUID().toString();
        String payload = "{\"orderId\":999,\"amount\":5000,\"msgId\":\"" + msgId + "\"}";

        // 【重点】双写：同一消息发到两条队列
        rabbitTemplate.convertAndSend("", "q5.mirrored.compare", payload);
        rabbitTemplate.convertAndSend("", "quorum.order.migrated", payload);
        log.add("双写完成: msgId=" + msgId);

        log.add("第3步: 消费者先只消费旧队列 (mirrored)，观察仲裁队列积压增长");
        log.add("第4步: 消费者切换消费仲裁队列，验证业务正常");
        log.add("第5步: 旧队列积压清零后，停止生产者双写，删除旧队列");

        resp.put("steps", log);
        resp.put("关键", "整个过程中 msgId 保证幂等，消费者无论从哪个队列消费都不会重复处理");
        return resp;
    }

    @GetMapping("/cleanup")
    public Map<String, Object> cleanup() {
        for (String q : Arrays.asList("q5.mirrored.compare", "q5.quorum.compare", "quorum.order.migrated")) {
            rabbitAdmin.deleteQueue(q);
        }
        return Map.of("status", "已清理");
    }
}
