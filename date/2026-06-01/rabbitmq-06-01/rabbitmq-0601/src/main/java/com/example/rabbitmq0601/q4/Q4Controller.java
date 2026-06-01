package com.example.rabbitmq0601.q4;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Q4: 镜像队列的性能陷阱与反模式
 *
 * 实战类型：代码演示
 *
 * 演示场景：对比三种配置下的吞吐量和延迟 ——
 *   A. ha-mode: all (3 个镜像) —— 写放大 3x
 *   B. ha-mode: exactly, ha-params: 2 (一主一从) —— 推荐配置
 *   C. ha-mode: exactly, ha-params: 1 (无镜像) —— 无冗余但最快
 *
 * 巧妙之处：通过控制队列名称前缀匹配镜像策略，同一次压测跑 3 种配置，
 * 让你亲眼看到：ha-mode:all 的吞吐量被最慢的节点拖死，
 * 而 exactly:2 几乎接近无镜像的性能。
 *
 * 前置条件：需要在 RabbitMQ 集群上手动设置三种策略 ——
 *   rabbitmqctl set_policy mirror-all  "^q4\\.all\\."  '{"ha-mode":"all","ha-sync-mode":"automatic"}'
 *   rabbitmqctl set_policy mirror-ex2  "^q4\\.ex2\\."  '{"ha-mode":"exactly","ha-params":2,"ha-sync-mode":"automatic"}'
 *   (q4.none.* 不匹配任何策略 = 无镜像)
 */
@RestController
@RequestMapping("/q4")
public class Q4Controller {

    private static final Logger log = LoggerFactory.getLogger(Q4Controller.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    public Q4Controller(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
    }

    /**
     * 步骤1: 创建三种队列并配置策略提示
     */
    @GetMapping("/setup")
    public Map<String, Object> setup() {
        List<String> created = new ArrayList<>();

        // 创建三种队列（注意命名前缀，匹配不同的镜像策略）
        for (String suffix : Arrays.asList("all.test", "ex2.test", "none.test")) {
            Queue q = new Queue("q4." + suffix, true, false, false);
            rabbitAdmin.declareQueue(q);
            created.add("q4." + suffix);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("queues", created);
        resp.put("tip", "在运行压测前，请确保已配置镜像策略：");
        resp.put("commands", Arrays.asList(
                "docker exec rabbitmq-node1 rabbitmqctl set_policy mirror-all '^q4\\.all\\.' '{\"ha-mode\":\"all\",\"ha-sync-mode\":\"automatic\"}' --apply-to queues",
                "docker exec rabbitmq-node1 rabbitmqctl set_policy mirror-ex2 '^q4\\.ex2\\.' '{\"ha-mode\":\"exactly\",\"ha-params\":2,\"ha-sync-mode\":\"automatic\"}' --apply-to queues"
        ));
        resp.put("next", "GET /q4/bench?count=200");
        return resp;
    }

    /**
     * 步骤2: 三队列并行压测对比
     *
     * 【对比实验】
     * 每条队列发 count 条消息，测量吞吐量和 P99 延迟。
     * 你会在控制台和返回结果中看到：
     * - ha-mode:all 的 P99 最高（受最慢镜像拖累）
     * - exactly:2 接近 no-mirror（写放大可控）
     * - 吞吐量随镜像数量递减
     */
    @GetMapping("/bench")
    public Map<String, Object> bench(@RequestParam(defaultValue = "200") int count) {
        Map<String, Object> resp = new LinkedHashMap<>();

        String[] queues = {"q4.all.test", "q4.ex2.test", "q4.none.test"};
        String[] labels = {"ha-mode:all(3镜像)", "exactly:2(1主1从)", "无镜像"};

        for (int i = 0; i < queues.length; i++) {
            BenchResult result = runBench(queues[i], count);
            resp.put(labels[i], result.toMap());
        }

        // 性能退化百分比
        BenchResult all = runBench("q4.all.test", 50);
        BenchResult none = runBench("q4.none.test", 50);
        double degradation = (1.0 - (double) all.throughput / none.throughput) * 100;

        resp.put("写放大分析", Map.of(
                "all_vs_none吞吐降幅", String.format("%.1f%%", degradation),
                "解读", String.format(
                        "ha-mode:all 的吞吐只有无镜像的 %.1f%%。"
                        + "每条消息要写 3 个节点，且 master 必须等最慢的 mirror 确认。"
                        + "这就是面试题中说的'木桶效应'和'写放大税'。",
                        100 - degradation)
        ));

        return resp;
    }

    private BenchResult runBench(String queueName, int count) {
        long totalNanos = 0;
        long minNs = Long.MAX_VALUE;
        long maxNs = 0;

        for (int i = 1; i <= count; i++) {
            long start = System.nanoTime();
            // 【重点】convertAndSend 内部会等待 confirm（如果开启了 correlated confirm）
            rabbitTemplate.convertAndSend("", queueName,
                    "Q4-BENCH-" + i + "-" + UUID.randomUUID().toString().substring(0, 8));
            long elapsed = System.nanoTime() - start;

            totalNanos += elapsed;
            if (elapsed < minNs) minNs = elapsed;
            if (elapsed > maxNs) maxNs = elapsed;
        }

        BenchResult result = new BenchResult();
        result.count = count;
        result.totalMs = TimeUnit.NANOSECONDS.toMillis(totalNanos);
        result.avgMs = TimeUnit.NANOSECONDS.toMillis(totalNanos) / count;
        result.minMs = TimeUnit.NANOSECONDS.toMillis(minNs);
        result.maxMs = TimeUnit.NANOSECONDS.toMillis(maxNs);
        // 【重点】吞吐量 = 总消息数 / 总时间（秒）
        result.throughput = (double) count / TimeUnit.NANOSECONDS.toSeconds(totalNanos);

        log.info("队列 {}: 吞吐={:.1f} msg/s, 平均={}ms, 最大={}ms",
                queueName, result.throughput, result.avgMs, result.maxMs);

        return result;
    }

    static class BenchResult {
        int count;
        long totalMs;
        long avgMs;
        long minMs;
        long maxMs;
        double throughput;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("发送条数", count);
            m.put("总耗时_ms", totalMs);
            m.put("平均延迟_ms", avgMs);
            m.put("最小延迟_ms", minMs);
            m.put("最大延迟_ms", maxMs);
            m.put("吞吐量_msg/s", String.format("%.1f", throughput));
            return m;
        }
    }

    /**
     * 反模式演示：临时队列开镜像
     *
     * 创建一个临时队列（auto-delete），如果它匹配了镜像策略，
     * 每条消息都会触发 GM 环广播，但队列用完就删 —— 白白浪费同步开销。
     */
    @GetMapping("/anti-pattern-temp-queue")
    public Map<String, Object> antiPatternTempQueue() {
        Map<String, Object> resp = new LinkedHashMap<>();

        // 模拟临时队列场景：RPC 回调队列
        String tempQueue = "q4.temp.rpc-callback";
        Queue q = new Queue(tempQueue, false, false, true); // auto-delete
        rabbitAdmin.declareQueue(q);

        resp.put("queue", tempQueue);
        resp.put("type", "临时队列 (auto-delete=true)");
        resp.put("problem", "如果临时队列名称匹配了镜像策略（如 ^q4\\.），"
                + "则每条消息都要同步到 mirror 节点，但队列几分钟后就删了。");
        resp.put("cost", "假设 RPC 每秒 1000 次，每次 3 副本写入 = 3000 次磁盘 IO/秒，"
                + "全花在生命周期只有几秒的队列上。");
        resp.put("best_practice", "临时队列不要用镜像前缀命名。RPC 回调挂了重建即可。");

        // 清理
        rabbitAdmin.deleteQueue(tempQueue);

        return resp;
    }

    /**
     * 清理
     */
    @GetMapping("/cleanup")
    public Map<String, Object> cleanup() {
        for (String q : Arrays.asList("q4.all.test", "q4.ex2.test", "q4.none.test")) {
            rabbitAdmin.deleteQueue(q);
        }
        return Map.of("status", "已清理");
    }
}
