package com.example.rabbitmq0601.q2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Q2: 集群模式下的消息路由与性能陷阱
 *
 * 实战类型：代码演示
 *
 * 核心机制：Spring Boot 连 5672(node1) → queueA 的 master 在 node1（本地路由），
 * queueB 通过 rabbitmqadmin 声明到 node3 → master 在 node3（跨节点路由）。
 * 然后从同一个 Spring Boot（连 node1）向两条队列各发 N 条消息，测量延迟差异。
 */
@RestController
@RequestMapping("/q2")
public class Q2Controller {

    private static final Logger log = LoggerFactory.getLogger(Q2Controller.class);
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    public Q2Controller(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
    }

    // ==================== 步骤1: 创建本地队列 + 给出跨节点创建指令 ====================

    /**
     * 声明 q2.queueA（本地）—— master 必然在 Spring Boot 连接的节点(node1)
     * 同时输出命令让用户手动在 node3 上声明 q2.queueB（跨节点）
     */
    @GetMapping("/setup")
    public Map<String, Object> setup() {
        // 先清理
        rabbitAdmin.deleteQueue("q2.queueA");
        rabbitAdmin.deleteQueue("q2.queueB");

        // 【重点】队列A —— Spring Boot 声明，master 必然在当前连接节点(node1)
        rabbitAdmin.declareQueue(new Queue("q2.queueA", true, false, false));

        // 【重点】队列B —— 必须用 docker exec 在 node3 上声明，强制 master=node3
        String cmdQueueB = "docker exec rabbitmq-node3 rabbitmqadmin declare queue "
                + "name=q2.queueB durable=true";

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("queueA", "q2.queueA — master 在 node1（通过 Spring Boot 声明，本地路由）");
        resp.put("queueB", "q2.queueB — master 在 node3（必须手动执行下方命令，跨节点路由）");
        resp.put("请执行此命令创建 queueB", cmdQueueB);
        resp.put("执行后验证", "docker exec rabbitmq-node1 rabbitmqctl list_queues name node | grep q2");
        resp.put("next", "GET /q2/bench?count=200  ← 压测对比");
        return resp;
    }

    // ==================== 步骤2【反面实验】: 暴露跨节点延迟 ====================

    @GetMapping("/bench")
    public Map<String, Object> bench(@RequestParam(defaultValue = "200") int count) {
        Map<String, Object> resp = new LinkedHashMap<>();

        // 【重点】同一次调用、同一个连接(node1)、同样的消息体
        // queueA: master=node1 → 本地路由
        // queueB: master=node3 → 跨节点路由（多一跳集群内部转发）
        BenchResult resultA = measure("q2.queueA", count);
        BenchResult resultB = measure("q2.queueB", count);

        resp.put("q2.queueA_本地路由", resultA.toMap());
        resp.put("q2.queueB_跨节点路由", resultB.toMap());

        long diffP50 = Math.abs(resultB.p50 - resultA.p50);
        long diffP99 = Math.abs(resultB.p99 - resultA.p99);

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("P50差异_ms", diffP50);
        analysis.put("P99差异_ms", diffP99);
        if (diffP99 > 2) {
            analysis.put("结论", String.format(
                    "跨节点路由 P99 慢 %dms。每条消息路径："
                    + "Spring Boot → node1:5672 → 集群内部转发 → node3(master 写入) → confirm 返回。"
                    + "多了一跳集群内部网络传输。", diffP99));
        } else {
            analysis.put("结论", "P99 差异 ≤ 2ms —— queueB 的 master 可能不在 node3。"
                    + "请确认已执行 /setup 中提示的 docker exec 命令。");
        }
        resp.put("分析", analysis);
        resp.put("next_正面优化", "GET /q2/optimize");
        return resp;
    }

    private BenchResult measure(String queueName, int count) {
        List<Long> latencies = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            long start = System.nanoTime();
            rabbitTemplate.convertAndSend("", queueName, "Q2-BENCH-" + i);
            latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }

        Collections.sort(latencies);
        BenchResult r = new BenchResult();
        r.count = count;
        r.min = latencies.isEmpty() ? 0 : latencies.get(0);
        r.p50 = latencies.isEmpty() ? 0 : latencies.get(count / 2);
        r.p99 = latencies.isEmpty() ? 0 : latencies.get((int) (count * 0.99));
        r.max = latencies.isEmpty() ? 0 : latencies.get(count - 1);
        r.avg = latencies.isEmpty() ? 0 : latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        return r;
    }

    // ==================== 步骤3【正面实验】: 优化策略 ====================

    @GetMapping("/optimize")
    public Map<String, Object> optimize() {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<String> strategies = new ArrayList<>();

        strategies.add("策略1: queue-master-locator = client-local（推荐）");
        strategies.add("  命令: rabbitmqctl set_policy master-local"
                + " '^q2\\.local\\.' '{\"queue-master-locator\":\"client-local\"}' --apply-to queues");
        strategies.add("  效果: 以 q2.local. 开头的队列，master 一定在客户端所连节点 → 零跨节点转发");

        strategies.add("");
        strategies.add("策略2: 客户端直连 master 所在节点");
        strategies.add("  API: curl -s http://admin:admin123@192.168.3.100:15672/api/queues/%2F/q2.queueB | jq '.node'");
        strategies.add("  效果: 生产者和队列 master 在同一节点，适用于固定队列场景");

        strategies.add("");
        strategies.add("策略3: 一致性哈希交换器");
        strategies.add("  含义: 用 x-consistent-hash 交换器将消息分片到不同队列，"
                + "每个队列 master 提前规划到不同节点");
        strategies.add("  效果: 负载天然均衡，避免所有 master 扎堆一个节点");

        resp.put("优化策略", strategies);
        resp.put("一句话总结", "不用策略 → P99 多 2-8ms。用 client-local → P99 接近本地路由。");
        return resp;
    }

    @GetMapping("/cleanup")
    public Map<String, Object> cleanup() {
        rabbitAdmin.deleteQueue("q2.queueA");
        rabbitAdmin.deleteQueue("q2.queueB");
        Map<String, Object> result = new HashMap<>();
        result.put("status", "已清理");
        return result;
    }

    static class BenchResult {
        int count;
        long min, p50, p99, max;
        double avg;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("发送条数", count);
            m.put("min_ms", min);
            m.put("P50_ms", p50);
            m.put("P99_ms", p99);
            m.put("max_ms", max);
            m.put("avg_ms", String.format("%.1f", avg));
            return m;
        }
    }
}
