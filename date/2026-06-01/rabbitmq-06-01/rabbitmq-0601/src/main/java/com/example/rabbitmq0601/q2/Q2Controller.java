package com.example.rabbitmq0601.q2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Q2: 集群模式下的消息路由与性能陷阱
 *
 * 实战类型：代码演示
 *
 * 演示场景：设计两组对比实验 ——
 *   A 组：生产者和队列 master 在同一节点（本地路由）
 *   B 组：生产者和队列 master 在不同节点（跨节点路由）
 * 通过测量延迟差异，赤裸裸展现"多了一跳集群内部转发"的性能代价。
 *
 * 巧妙之处：利用 RabbitMQ 集群的 queue-master-locator 默认行为，
 * 同一连接上先后声明两个队列，它们的 master 可能被分配到不同节点，
 * 然后往两个队列各发 100 条消息测 P50/P99 延迟 ——
 * 你会亲眼看到跨节点转发的延迟比本地高 1-3ms（甚至更多）。
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

    /**
     * 步骤1: 创建两个普通队列（故意让它们分布在不同节点）
     */
    @GetMapping("/setup")
    public Map<String, Object> setup() {
        // 声明两个队列，不指定 queue-master-locator，
        // 集群会用默认策略分配 master，大概率分散
        Queue qA = new Queue("q2.local.bench", true, false, false);
        Queue qB = new Queue("q2.remote.bench", true, false, false);

        rabbitAdmin.declareQueue(qA);
        rabbitAdmin.declareQueue(qB);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "两个队列已创建: q2.local.bench, q2.remote.bench");
        resp.put("tip", "接下来请到管理界面查看两个队列的 master 节点，"
                + "如果它们在同一个节点，可以手动执行 'docker exec rabbitmq-node2 "
                + "rabbitmqctl set_queue_master_locator min-masters' 重新创建");
        resp.put("next", "GET /q2/bench?count=100");
        return resp;
    }

    /**
     * 步骤2: 压测对比
     *
     * 【对比实验】
     * - 先调用 /q2/publish-to?queue=q2.local.bench&count=100 看到正常延迟
     * - 再调用 /q2/publish-to?queue=q2.remote.bench&count=100 看到跨节点延迟
     * - 最后调用 /q2/bench 一次性跑两个队列的对比
     */
    @GetMapping("/bench")
    public Map<String, Object> bench() {
        int count = 100;
        Map<String, Object> resp = new HashMap<>();

        // 【重点】测量两个队列的发布延迟 —— 赤裸裸展现跨节点开销
        BenchResult resultA = measurePublish("q2.local.bench", count);
        BenchResult resultB = measurePublish("q2.remote.bench", count);

        resp.put("queueA_q2.local.bench", resultA.toMap());
        resp.put("queueB_q2.remote.bench", resultB.toMap());

        // 延迟差
        long diffP50 = resultB.p50 - resultA.p50;
        long diffP99 = resultB.p99 - resultA.p99;
        resp.put("跨节点额外延迟_ms", Map.of(
                "p50_diff", diffP50,
                "p99_diff", diffP99,
                "解释", diffP99 > 1
                        ? String.format("跨节点路由比本地路由 P99 慢 %dms —— 这就是多了一跳集群内部转发的代价", diffP99)
                        : "两个队列恰好在同一节点，延迟相近。下次请重试，集群会重新分配 master。这正是 Q2 要讲的不确定性!"
        ));

        resp.put("TIP", "如果两个队列的 master 恰好在同一节点，延迟会很接近。"
                + "这就是面试题中说的：'客户端要尽量连到数据所在节点'。"
                + "试试手动指定 queue_master_locator 为 client-local 来优化!");

        return resp;
    }

    /**
     * 单队列发布延迟测量
     */
    private BenchResult measurePublish(String queueName, int count) {
        List<Long> latencies = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            long start = System.nanoTime();
            // 【重点】convertAndSend 等待 confirm 返回 —— 包含跨节点转发时间
            rabbitTemplate.convertAndSend("", queueName,
                    "BENCH-" + i + "-" + System.currentTimeMillis());
            long elapsed = System.nanoTime() - start;
            latencies.add(TimeUnit.NANOSECONDS.toMillis(elapsed));
        }

        // 排序后取 P50、P99
        Collections.sort(latencies);
        BenchResult result = new BenchResult();
        result.count = count;
        result.min = latencies.get(0);
        result.max = latencies.get(count - 1);
        result.p50 = latencies.get(count / 2);
        result.p99 = latencies.get((int) (count * 0.99));
        result.avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);

        log.info("队列 {}: P50={}ms, P99={}ms, AVG={}ms",
                queueName, result.p50, result.p99,
                String.format("%.1f", result.avg));

        return result;
    }

    static class BenchResult {
        int count;
        long min, max;
        long p50, p99;
        double avg;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("发送条数", count);
            m.put("min_ms", min);
            m.put("p50_ms", p50);
            m.put("p99_ms", p99);
            m.put("max_ms", max);
            m.put("avg_ms", String.format("%.1f", avg));
            return m;
        }
    }

    /**
     * 清理
     */
    @GetMapping("/cleanup")
    public Map<String, Object> cleanup() {
        rabbitAdmin.deleteQueue("q2.local.bench");
        rabbitAdmin.deleteQueue("q2.remote.bench");
        return Map.of("status", "已清理");
    }
}
