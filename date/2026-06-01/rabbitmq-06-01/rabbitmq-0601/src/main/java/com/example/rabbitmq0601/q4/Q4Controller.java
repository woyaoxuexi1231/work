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
                "q4.all.bench  → 匹配 ^q4\\.all\\. → ha-mode:all（3 副本）",
                "q4.ex2.bench  → 匹配 ^q4\\.ex2\\. → exactly:2（1 主 1 从）",
                "q4.none.bench → 无策略（裸奔基线）"
        ));
        resp.put("前置", "sudo bash scripts/02-mirrored-queue.sh（已配置 q4-all 和 q4-ex2 策略）");
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

        // 用平均延迟对比（不用吞吐，避免数值溢出）
        Map<String, Object> analysis = new LinkedHashMap<>();
        double allVsNone = rNone.avgMs > 0 ? (double) rAll.avgMs / rNone.avgMs : 1.0;
        double ex2VsNone = rNone.avgMs > 0 ? (double) rEx2.avgMs / rNone.avgMs : 1.0;
        double allVsNoneP99 = rNone.p99 > 0 ? (double) rAll.p99 / rNone.p99 : 1.0;

        analysis.put("all_avg_vs_无镜像", String.format("%.1fx", allVsNone));
        analysis.put("ex2_avg_vs_无镜像", String.format("%.1fx", ex2VsNone));
        analysis.put("all_P99_vs_无镜像", String.format("%.1fx", allVsNoneP99));

        if (allVsNone > 1.5) {
            analysis.put("结论", String.format(
                    "ha-mode:all 平均延迟是无镜像的 %.1f 倍，P99 是 %.1f 倍。"
                    + "每条消息写 3 个节点，master 等最慢 mirror → 写放大严重。"
                    + "exactly:2 只慢 %.1f 倍，却有备胎。",
                    allVsNone, allVsNoneP99, ex2VsNone));
        } else {
            analysis.put("结论", "三组差距不大。尝试增大 count 或混用 SSD/HDD 节点再测。");
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
            rabbitTemplate.convertAndSend("", queueName, "Q4-BENCH-" + i);
            long elapsed = System.nanoTime() - start;
            totalNs += elapsed;
            if (elapsed > maxNs) maxNs = elapsed;
        }

        BenchResult r = new BenchResult();
        r.count = count;
        r.totalMs = TimeUnit.NANOSECONDS.toMillis(totalNs);
        // 健壮：totalNs==0 时避免 Infinity
        r.avgMs     = totalNs > 0 ? TimeUnit.NANOSECONDS.toMillis(totalNs) / count : 0;
        r.p99       = TimeUnit.NANOSECONDS.toMillis(maxNs);
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

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("发送条数", count);
            m.put("总耗时_ms", totalMs);
            m.put("平均_ms", avgMs);
            m.put("P99_ms (max)", p99);
            return m;
        }
    }
}
