package com.redis.demo.q4_cross_slot_transaction;

import com.redis.demo.q4_cross_slot_transaction.SagaCompensationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Q4: 跨槽事务与 SAGA 补偿 —— HTTP 接口.
 *
 * <h3>对比实验路径</h3>
 * <pre>
 *   # 1. 跨槽 Lua 被拒——亲眼看到三个 key 散落不同 slot
 *   curl 'http://localhost:8080/api/cluster/transaction/cross-slot?orderId=phone-123'
 *
 *   # 2. Hash Tag 强制同槽——看看风险和副作用
 *   curl 'http://localhost:8080/api/cluster/transaction/hash-tag?orderId=phone-123'
 *
 *   # 3. SAGA 事务——成功路径
 *   curl -X POST 'http://localhost:8080/api/cluster/transaction/saga/execute?orderId=order-001'
 *
 *   # 4. SAGA 事务——失败触发补偿（注意 orderId 包含 "fail"）
 *   curl -X POST 'http://localhost:8080/api/cluster/transaction/saga/execute?orderId=fail-order-002'
 *
 *   # 5. 查看 SAGA 日志
 *   curl 'http://localhost:8080/api/cluster/transaction/saga/log?orderId=order-001'
 *
 *   # 6. 最近的事务列表
 *   curl 'http://localhost:8080/api/cluster/transaction/saga/recent?limit=10'
 * </pre>
 */
@RestController
@RequestMapping("/api/cluster/transaction")
public class ClusterTransactionController {

    private final SagaCompensationService sagaService;

    public ClusterTransactionController(SagaCompensationService sagaService) {
        this.sagaService = sagaService;
    }

    /**
     * 【核心对比1】跨槽 Lua 被拒——错误示范.
     *
     * 观察返回值中三个 key 的 slot 是否相同。
     * 不相同 → CROSSSLOT error。
     */
    @GetMapping("/cross-slot")
    public Map<String, Object> crossSlot(
            @RequestParam(defaultValue = "phone-123") String orderId) {
        return sagaService.simulateCrossSlotRejection(orderId);
    }

    /**
     * 【核心对比2】Hash Tag 解决方案——有风险的"正确".
     *
     * 观察返回值中 risks 字段：热点、数据倾斜、扩展性丧失。
     */
    @GetMapping("/hash-tag")
    public Map<String, Object> hashTag(
            @RequestParam(defaultValue = "phone-123") String orderId) {
        return sagaService.demonstrateHashTagSolution(orderId);
    }

    /**
     * 【核心对比3】SAGA 事务——推荐的正确实现.
     *
     * 对比实验：
     *   orderId 包含 "fail" → 触发补偿回滚
     *   orderId 不含 "fail" → 全部成功
     *
     * 观察 timeline 中 step 字段的流转，以及 COMPENSATE 步骤的触发。
     */
    @PostMapping("/saga/execute")
    public Map<String, Object> sagaExecute(
            @RequestParam(defaultValue = "order-001") String orderId) {
        return sagaService.executeSagaTransaction(orderId);
    }

    /** 查看 SAGA 事务日志. */
    @GetMapping("/saga/log")
    public List<Map<String, Object>> sagaLog(
            @RequestParam String orderId) {
        return sagaService.getSagaLog(orderId);
    }

    /** 最近的事务. */
    @GetMapping("/saga/recent")
    public List<Map<String, Object>> sagaRecent(
            @RequestParam(defaultValue = "10") int limit) {
        return sagaService.getRecentSagas(limit);
    }
}
