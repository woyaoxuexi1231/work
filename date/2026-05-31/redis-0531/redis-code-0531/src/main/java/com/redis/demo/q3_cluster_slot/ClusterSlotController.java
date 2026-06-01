package com.redis.demo.q3_cluster_slot;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Q3: Redis Cluster 槽位路由 —— HTTP 接口.
 *
 * <h3>对比实验路径</h3>
 * <pre>
 *   # 1. 查看集群拓扑
 *   curl http://localhost:8080/api/cluster/slot/topology
 *
 *   # 2. 计算单个 key 的槽位
 *   curl 'http://localhost:8080/api/cluster/slot/calc?key=order:123'
 *
 *   # 3. 批量计算——观察不同 key 散列到不同槽
 *   curl -X POST http://localhost:8080/api/cluster/slot/batch \
 *     -H 'Content-Type: application/json' \
 *     -d '["order:1","order:2","inventory:1","user:session:abc"]'
 *
 *   # 4. MOVED 重定向模拟
 *   curl 'http://localhost:8080/api/cluster/slot/moved?key=order:999'
 *
 *   # 5. ASK 重定向模拟
 *   curl 'http://localhost:8080/api/cluster/slot/ask?key=order:999'
 *
 *   # 6. MOVED vs ASK 对比
 *   curl http://localhost:8080/api/cluster/slot/moved-vs-ask
 *
 *   # 7. Hash Tag 演示——亲眼看到 {tag} 如何强制同槽
 *   curl 'http://localhost:8080/api/cluster/slot/hash-tag-demo?tag=order:123'
 *
 *   # 8. Hash Tag 提取规则
 *   curl http://localhost:8080/api/cluster/slot/hash-tag-examples
 * </pre>
 */
@RestController
@RequestMapping("/api/cluster/slot")
public class ClusterSlotController {

    private final SlotCalculator calculator;

    public ClusterSlotController(SlotCalculator calculator) {
        this.calculator = calculator;
    }

    /** 查看集群拓扑 & 为什么是 16384. */
    @GetMapping("/topology")
    public Map<String, Object> topology() {
        return calculator.clusterTopology();
    }

    /** 计算单个 key 的槽位. */
    @GetMapping("/calc")
    public Map<String, Object> calc(@RequestParam String key) {
        return calculator.locateKey(key);
    }

    /** 批量计算槽位. */
    @PostMapping("/batch")
    public Map<String, Object> batch(@RequestBody List<String> keys) {
        return calculator.locateKeys(keys);
    }

    /** MOVED 重定向模拟. */
    @GetMapping("/moved")
    public Map<String, Object> moved(@RequestParam(defaultValue = "order:999") String key) {
        return calculator.simulateMoved(key);
    }

    /** ASK 重定向模拟. */
    @GetMapping("/ask")
    public Map<String, Object> ask(@RequestParam(defaultValue = "order:999") String key) {
        return calculator.simulateAsk(key);
    }

    /** MOVED vs ASK 对比. */
    @GetMapping("/moved-vs-ask")
    public Map<String, Object> movedVsAsk() {
        return calculator.movedVsAsk();
    }

    /** Hash Tag 演示——强制同槽. */
    @GetMapping("/hash-tag-demo")
    public Map<String, Object> hashTagDemo(
            @RequestParam(defaultValue = "order:123") String tag) {
        return calculator.demonstrateHashTag(tag);
    }

    /** Hash Tag 提取规则示例. */
    @GetMapping("/hash-tag-examples")
    public Map<String, Object> hashTagExamples() {
        return calculator.hashTagExamples();
    }
}
