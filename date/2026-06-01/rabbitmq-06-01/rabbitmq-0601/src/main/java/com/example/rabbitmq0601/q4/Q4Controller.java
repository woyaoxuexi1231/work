package com.example.rabbitmq0601.q4;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Q4: 镜像队列的性能陷阱与反模式
 *
 * 实战类型：代码演示
 *
 * 演示场景：三组队列共存在同一集群，唯一变量是镜像数量 ——
 *   q4.all.*  → ha-mode: all（3 副本，写放大 3x）
 *   q4.ex2.*  → ha-mode: exactly, ha-params: 2（1 主 1 从，推荐配置）
 *   q4.none.* → 无镜像（裸奔基线）
 *
 * 对比实验：
 *   反面：ha-mode:all → 吞吐被最慢 mirror 拖死
 *   正面：exactly:2   → 接近无镜像性能，却有备胎
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

    // ==================== 步骤1: 创建队列 + 提示配置策略 ====================

    @GetMapping("/setup")
    public Map<String, Object> setup() {
        // 清理旧队列
        for (String q : Arrays.asList("q4.all.bench", "q4.ex2.bench", "q4.none.bench")) {
            rabbitAdmin.deleteQueue(q);
        }

        // 创建三组队列（名称前缀匹配不同策略）
        rabbitAdmin.declareQueue(new Queue("q4.all.bench",  true, false, false));
        rabbitAdmin.declareQueue(new Queue("q4.ex2.bench",  true, false, false));
        rabbitAdmin.declareQueue(new Queue("q4.none.bench", true, false, false));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("queues", Arrays.asList(
                "q4.all.bench  → 需配 ha-mode:all",
                "q4.ex2.bench  → 需配 ha-mode:exactly, ha-params:2",
                "q4.none.bench → 无需策略（基线）"
        ));
        resp.put("请执行以下两条命令配置策略", Arrays.asList(
                "docker exec rabbitmq-node1 rabbitmqctl set_policy q4-all '^q4\\.all\\.' "
                        + "'{\"ha-mode\":\"all\",\"ha-sync-mode\":\"automatic\"}' --apply-to queues",
                "docker exec rabbitmq-node1 rabbitmqctl set_policy q4-ex2 '^q4\\.ex2\\.' "
                        + "'{\"ha-mode\":\"exactly\",\"ha-params\":2,\"ha-sync-mode\":\"automatic\"}' --apply-to queues"
        ));
        resp.put("验证策略", "docker exec rabbitmq-node1 rabbitmqctl list_policies");
        resp.put("验证镜像", "去管理界面查看 q4.all.bench → 应有 2 个 slave；q4.ex2.bench → 1 个 slave；q4.none.bench → 0 个 slave");
        resp.put("next_反面实验", "GET /q4/bench?count=200  ← 三组并行压测");
        return resp;
    }

    // ==================== 步骤2【反面实验】: 三组压测对比 ====================

    @GetMapping("/bench")
    public Map<String, Object> bench(@RequestParam(defaultValue = "200") int count) {
        Map<String, Object> resp = new LinkedHashMap<>();

        // 【重点】三个队列顺序压测，共享同一连接、同一集群、同一时刻
        // 唯一变量：镜像策略
        BenchResult rAll  = measure("q4.all.bench",  count);
        BenchResult rEx2  = measure("q4.ex2.bench",  count);
        BenchResult rNone = measure("q4.none.bench", count);

        resp.put("ha-mode_all_3副本",  rAll.toMap());
        resp.put("exactly_2_1主1从",   rEx2.toMap());
        resp.put("无镜像_基线",         rNone.toMap());

        // 【重点】计算写放大代价
        double degradation = rNone.throughput > 0
                ? (1.0 - rAll.throughput / rNone.throughput) * 100 : 0;

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("all吞吐降幅_vs_无镜像", String.format("%.1f%%", degradation));
        analysis.put("all_P99_vs_无镜像", (rNone.p99 > 0 ? String.format("%.1fx", (double) rAll.p99 / rNone.p99) : "N/A"));
        analysis.put("ex2_P99_vs_无镜像", (rNone.p99 > 0 ? String.format("%.1fx", (double) rEx2.p99 / rNone.p99) : "N/A"));

        if (degradation > 20) {
            analysis.put("结论", String.format(
                    "ha-mode:all 吞吐量只有无镜像的 %.0f%%，P99 是无镜像的 %.1f 倍。"
                    + "每条消息写 3 个节点，且 master 必须等最慢的 mirror 确认。"
                    + "而 exactly:2 只比无镜像慢一点点，却多了一个备胎。",
                    100 - degradation, (double) rAll.p99 / Math.max(1, rNone.p99)));
        } else {
            analysis.put("结论", "三组差距不大，可能 master 恰好在同一节点或磁盘性能一致。"
                    + "尝试增大 count 到 500 或混用 SSD/HDD 节点再测。");
        }

        resp.put("分析", analysis);
        resp.put("next_正面优化", "GET /q4/anti-pattern  ← 临时队列镜像反模式");
        return resp;
    }

    private BenchResult measure(String queueName, int count) {
        long totalNs = 0;
        long maxNs = 0;

        for (int i = 1; i <= count; i++) {
            long start = System.nanoTime();
            // 【重点】convertAndSend 的耗时含 confirm，在 all 模式下 = 等 3 个节点全部确认
            rabbitTemplate.convertAndSend("", queueName, "Q4-BENCH-" + i);
            long elapsed = System.nanoTime() - start;
            totalNs += elapsed;
            if (elapsed > maxNs) maxNs = elapsed;
        }

        BenchResult r = new BenchResult();
        r.count = count;
        r.totalMs = TimeUnit.NANOSECONDS.toMillis(totalNs);
        r.avgMs   = TimeUnit.NANOSECONDS.toMillis(totalNs) / count;
        r.p99     = TimeUnit.NANOSECONDS.toMillis(maxNs);  // 简化：用 max 近似 P99
        r.throughput = (double) count / TimeUnit.NANOSECONDS.toSeconds(totalNs);
        return r;
    }

    // ==================== 步骤3: 反模式 —— 临时队列开镜像 ====================

    @GetMapping("/anti-pattern")
    public Map<String, Object> antiPattern() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("场景", "RPC 回调临时队列（auto-delete=true）—— 生命周期仅数秒");
        resp.put("反模式", "如果临时队列名称匹配了镜像策略（如 ^q4\\.），则每条消息都要 GM 环广播到 mirror");

        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("假设_RPC_QPS", "1000/s");
        cost.put("ha-mode_all_磁盘IO", "1000 × 3 = 3000 次磁盘写入/秒");
        cost.put("队列存活时间", "数秒（RPC 回调完成即删除）");
        cost.put("浪费", "3000 次/秒的磁盘 IO 全花在几秒后就删除的队列上");
        resp.put("代价", cost);

        resp.put("正确做法", "临时队列命名避开镜像前缀（如用 tmp. 或 rpc. 开头），"
                + "客户端监听连接中断后重建即可。");
        return resp;
    }

    @GetMapping("/cleanup")
    public Map<String, Object> cleanup() {
        for (String q : Arrays.asList("q4.all.bench", "q4.ex2.bench", "q4.none.bench")) {
            rabbitAdmin.deleteQueue(q);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "已清理");
        return result;
    }

    static class BenchResult {
        int count;
        long totalMs;
        long avgMs;
        long p99;
        double throughput;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("发送条数", count);
            m.put("总耗时_ms", totalMs);
            m.put("平均_ms", avgMs);
            m.put("P99_ms (max)", p99);
            m.put("吞吐_msg/s", String.format("%.1f", throughput));
            return m;
        }
    }
}
