package com.redis.demo.controller;

import com.redis.demo.ops.ReshardStateMachine;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Q8: 平滑扩缩容 —— HTTP 接口.
 *
 * <h3>对比实验路径</h3>
 * <pre>
 *   # 1. 生成扩容迁移计划（100→120 节点）
 *   curl 'http://localhost:8080/api/ops/reshard/plan?newNodeCount=20'
 *
 *   # 2. 执行单槽迁移
 *   curl -X POST 'http://localhost:8080/api/ops/reshard/migrate?slot=5000&source=7000&target=8000'
 *
 *   # 3. 查看迁移状态
 *   curl http://localhost:8080/api/ops/reshard/status
 *
 *   # 4. 暂停迁移（模拟 P99 飙升触发自动暂停）
 *   curl -X POST 'http://localhost:8080/api/ops/reshard/pause?reason=P99%E5%BB%B6%E8%BF%9F%E8%B6%85%E8%BF%8710ms'
 *
 *   # 5. 恢复迁移
 *   curl -X POST http://localhost:8080/api/ops/reshard/resume
 *
 *   # 6. 回滚某个槽的迁移
 *   curl -X POST 'http://localhost:8080/api/ops/reshard/rollback?slot=5000'
 *
 *   # 7. 大 Key 迁移风险演示（500MB key 的灾难）
 *   curl http://localhost:8080/api/ops/reshard/bigkey-risk
 * </pre>
 */
@RestController
@RequestMapping("/api/ops/reshard")
public class ReshardController {

    private final ReshardStateMachine stateMachine;

    public ReshardController(ReshardStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    /** 生成扩容迁移计划（贪心均衡算法）. */
    @GetMapping("/plan")
    public Map<String, Object> plan(@RequestParam(defaultValue = "20") int newNodeCount) {
        return stateMachine.generateRebalancePlan(newNodeCount);
    }

    /**
     * 【核心】执行单槽迁移——观察完整的状态机流转.
     */
    @PostMapping("/migrate")
    public Map<String, Object> migrate(@RequestParam int slot,
                                        @RequestParam String source,
                                        @RequestParam String target) {
        return stateMachine.executeSlotMigration(slot, source, target);
    }

    /** 查看迁移状态. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return stateMachine.getMigrationStatus();
    }

    /** 暂停迁移. */
    @PostMapping("/pause")
    public Map<String, Object> pause(@RequestParam(defaultValue = "P99延迟超过阈值") String reason) {
        return stateMachine.pauseMigration(reason);
    }

    /** 恢复迁移. */
    @PostMapping("/resume")
    public Map<String, Object> resume() {
        return stateMachine.resumeMigration();
    }

    /** 回滚迁移. */
    @PostMapping("/rollback")
    public Map<String, Object> rollback(@RequestParam int slot) {
        return stateMachine.rollbackSlot(slot);
    }

    /** 大 Key 迁移风险演示. */
    @GetMapping("/bigkey-risk")
    public Map<String, Object> bigKeyRisk() {
        return stateMachine.demonstrateBigKeyMigrationRisk();
    }
}
