package com.redis.demo.q8_smooth_reshard;

import com.redis.demo.q3_cluster_slot.SlotCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Q8: 平滑扩容——槽位迁移状态机 & 贪心均衡算法.
 *
 * <h3>核心原理</h3>
 * redis-cli --cluster reshard 的背后：
 * <ol>
 *   <li>目标节点: CLUSTER SETSLOT slot IMPORTING source —— "我准备好接收了"</li>
 *   <li>源节点:   CLUSTER SETSLOT slot MIGRATING target —— "这块地要搬走了"</li>
 *   <li>源节点执行 MIGRATE 命令，以 pipeline 逐个迁移 key</li>
 *   <li>全部 key 迁移完后: CLUSTER SETSLOT slot NODE target —— 全网广播所有权</li>
 *   <li>客户端遇到 ASK 重定向时会到目标节点请求</li>
 * </ol>
 *
 * <h3>状态机</h3>
 * <pre>
 *   PENDING → IMPORTING → MIGRATING → COMPLETED
 *                                   ↘ FAILED → ROLLBACK
 * </pre>
 *
 * <h3>贪心均衡算法</h3>
 * 不断找出流量最高的节点和最低的节点，将它们之间的一个 slot 迁移过去，
 * 直到所有节点流量标准差小于预设阈值。
 *
 * <h3>对应面试题 Q8</h3>
 * <ul>
 *   <li>reshard 背后原理</li>
 *   <li>大 Key 迁移时的主线程阻塞风险</li>
 *   <li>ASK 转向期间的业务正确性保证</li>
 * </ul>
 */
@Service
public class ReshardStateMachine {

    private static final Logger log = LoggerFactory.getLogger(ReshardStateMachine.class);

    private final JdbcTemplate jdbcTemplate;
    private final SlotCalculator slotCalculator;

    // 模拟的迁移状态
    private final List<Map<String, Object>> activeMigrations = new ArrayList<>();
    private boolean migrationPaused = false;
    private String pauseReason;

    public ReshardStateMachine(JdbcTemplate jdbcTemplate,
                               SlotCalculator slotCalculator) {
        this.jdbcTemplate = jdbcTemplate;
        this.slotCalculator = slotCalculator;
    }

    // ========================================================================
    // 迁移计划生成
    // ========================================================================

    /**
     * 【重点】贪心均衡算法——生成槽位迁移计划.
     *
     * 场景：100 节点集群，大促前扩容 20 节点。
     * 算法：不断将流量最高节点的槽迁移到流量最低节点，直到标准差收敛。
     */
    public Map<String, Object> generateRebalancePlan(int newNodeCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", "贪心均衡（Greedy Rebalance）");

        // 模拟当前集群：100 个节点 + 新增 20 个
        int currentNodes = 100;
        int totalNodes = currentNodes + newNodeCount;
        int totalSlots = 16384;

        result.put("currentNodes", currentNodes);
        result.put("newNodes", newNodeCount);
        result.put("totalNodes", totalNodes);
        result.put("totalSlots", totalSlots);

        // 当前每个节点平均槽数
        int currentAvgSlots = totalSlots / currentNodes;  // 163
        int newAvgSlots = totalSlots / totalNodes;         // 136

        result.put("currentAvgSlotsPerNode", currentAvgSlots);
        result.put("newAvgSlotsPerNode", newAvgSlots);
        result.put("slotsToMigrate", currentAvgSlots - newAvgSlots); // 27 个槽/节点

        // 生成迁移计划
        List<Map<String, Object>> plan = new ArrayList<>();
        int totalSlotsToMove = (currentAvgSlots - newAvgSlots) * currentNodes;
        int migrated = 0;

        for (int i = 0; i < Math.min(10, totalSlotsToMove); i++) {
            int slot = i * (totalSlots / totalSlotsToMove);
            String source = "node-" + (7000 + (i % currentNodes));
            String target = "node-" + (8000 + (i % newNodeCount));

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step", i + 1);
            step.put("slot", slot);
            step.put("sourceNode", source);
            step.put("targetNode", target);
            step.put("status", "PENDING");

            //【重点】迁移前检查——大 Key 拦截
            String bigKeyCheck = checkBigKeyBeforeMigration(slot);
            step.put("bigKeyCheck", bigKeyCheck);

            plan.add(step);
            migrated++;
        }

        result.put("plan", plan);
        result.put("planPreview", "前 " + Math.min(10, totalSlotsToMove)
                + " 个槽的迁移计划（总共需迁移 " + totalSlotsToMove + " 个槽）");

        // 均衡度评估
        result.put("balanceMetrics", new LinkedHashMap<String, Object>() {{
            put("beforeStdDev", "高——100 个节点部分满载");
            put("afterStdDev", "低——120 个节点均匀分布");
            put("convergence", "贪心算法 O(N*M) 复杂度，N=节点数，M=槽数");
        }});

        return result;
    }

    /**
     * 【重点】迁移前大 Key 检查——500MB 的迁移噩梦（Q8 血泪案例）.
     */
    private String checkBigKeyBeforeMigration(int slot) {
        // 模拟：某个 slot 包含 500MB 的大 Key
        if (slot == 5000) {
            return "⚠ WARNING——slot " + slot + " 检测到 500MB 大 Key！"
                    + "迁移会导致主线程阻塞，必须先拆分！";
        }
        if (slot % 2048 == 0) {
            return "⚠ CAUTION——slot " + slot + " 包含 100MB+ 的 key，"
                    + "建议低峰期迁移";
        }
        return "OK——未检测到大 Key";
    }

    // ========================================================================
    // 迁移状态机
    // ========================================================================

    /**
     * 【重点】执行单槽迁移——完整的 MIGRATE 状态机.
     */
    public Map<String, Object> executeSlotMigration(int slot, String sourceNode,
                                                     String targetNode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slot", slot);
        result.put("sourceNode", sourceNode);
        result.put("targetNode", targetNode);

        List<Map<String, Object>> timeline = new ArrayList<>();

        // 检查是否暂停
        if (migrationPaused) {
            result.put("status", "PAUSED");
            result.put("reason", pauseReason);
            return result;
        }

        // State 1: IMPORTING on target
        timeline.add(state("IMPORTING", "目标节点 " + targetNode
                + " 执行 CLUSTER SETSLOT " + slot + " IMPORTING " + sourceNode,
                "OK"));

        // State 2: MIGRATING on source
        timeline.add(state("MIGRATING", "源节点 " + sourceNode
                + " 执行 CLUSTER SETSLOT " + slot + " MIGRATING " + targetNode,
                "OK"));

        // State 3: MIGRATE keys——模拟逐 key 迁移
        List<String> sampleKeys = Arrays.asList(
                "order:{" + slot + "}:1",
                "cache:{" + slot + "}:data",
                "session:{" + slot + "}:user"
        );

        for (String key : sampleKeys) {
            timeline.add(state("MIGRATE_KEY",
                    "MIGRATE " + targetNode.split(":")[0] + " " + targetNode.split(":")[1]
                            + " " + key + " 0 5000",
                    "OK"));
        }

        // State 4: 通知全网——所有权正式交接
        timeline.add(state("COMPLETED",
                "CLUSTER SETSLOT " + slot + " NODE " + targetNode + " —— 全网广播",
                "OK"));

        // 持久化到 MySQL
        try {
            jdbcTemplate.update(
                    "INSERT INTO reshard_task (slot, source_node, target_node, state, key_count) "
                            + "VALUES (?, ?, ?, 'COMPLETED', ?) "
                            + "ON DUPLICATE KEY UPDATE state = 'COMPLETED', key_count = ?",
                    slot, sourceNode, targetNode, sampleKeys.size(), sampleKeys.size());
        } catch (Exception e) {
            log.warn("迁移日志写入失败: {}", e.getMessage());
        }

        result.put("status", "COMPLETED");
        result.put("timeline", timeline);
        result.put("keysMigrated", sampleKeys.size());
        result.put("clientImpact",
                "迁移期间客户端可能收到 ASK 重定向——"
                        + "Lettuce Smart Routing 自动处理，业务无感知");

        return result;
    }

    /**
     * 【重点】暂停迁移——应对 P99 飙升.
     *
     * 面试追问：迁移过程中延迟飙升怎么办？
     * 答：自动暂停 → 等集群平稳 → 继续
     */
    public Map<String, Object> pauseMigration(String reason) {
        migrationPaused = true;
        pauseReason = reason;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "PAUSED");
        r.put("reason", reason);
        r.put("autoResume", "每 5 分钟自动检查集群 P99 延迟，< 10ms 时自动恢复");
        return r;
    }

    /** 恢复迁移. */
    public Map<String, Object> resumeMigration() {
        migrationPaused = false;
        pauseReason = null;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "RESUMED");
        return r;
    }

    // ========================================================================
    // 回滚机制
    // ========================================================================

    /**
     * 【重点】迁移中断回滚——清理 MIGRATING/IMPORTING 状态.
     *
     * 面试追问：迁移中断了怎么回滚？
     * 答：1. 清除源节点的 MIGRATING 状态（CLUSTER SETSLOT slot STABLE）
     *    2. 清除目标节点的 IMPORTING 状态
     *    3. 已经迁移的 key 在目标节点上保留（可选删除）
     *    4. 从 MySQL 状态机恢复
     */
    public Map<String, Object> rollbackSlot(int slot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slot", slot);

        List<Map<String, Object>> rollbackSteps = new ArrayList<>();
        rollbackSteps.add(step("1", "检查 MySQL 中 slot " + slot + " 的最后状态",
                "SELECT state FROM reshard_task WHERE slot = " + slot));
        rollbackSteps.add(step("2", "源节点: CLUSTER SETSLOT " + slot + " STABLE",
                "清除 MIGRATING 状态"));
        rollbackSteps.add(step("3", "目标节点: CLUSTER SETSLOT " + slot + " STABLE",
                "清除 IMPORTING 状态"));
        rollbackSteps.add(step("4", "更新 MySQL 状态为 ROLLBACK",
                "UPDATE reshard_task SET state='ROLLBACK' WHERE slot = " + slot));

        result.put("rollbackSteps", rollbackSteps);
        result.put("status", "ROLLBACK_COMPLETED");
        result.put("note", "一键回滚脚本确保 1 分钟内恢复——这是大规模运维的底线能力");

        try {
            jdbcTemplate.update(
                    "UPDATE reshard_task SET state = 'ROLLBACK', error_msg = '手动回滚' "
                            + "WHERE slot = ?", slot);
        } catch (Exception ignored) {}

        return result;
    }

    /**
     * 查看迁移任务状态（从 MySQL 读取）.
     */
    public Map<String, Object> getMigrationStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("migrationPaused", migrationPaused);
        if (migrationPaused) result.put("pauseReason", pauseReason);

        try {
            List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                    "SELECT slot, source_node, target_node, state, key_count, error_msg, update_time "
                            + "FROM reshard_task ORDER BY slot LIMIT 20");
            result.put("tasks", tasks);
            result.put("taskCount", tasks.size());
        } catch (Exception e) {
            result.put("tasks", new ArrayList<>());
            result.put("note", "MySQL 未就绪——迁移状态机无法读取持久化数据");
        }

        result.put("stateMachine", new String[]{
                "PENDING → IMPORTING → MIGRATING → COMPLETED",
                "                      ↘ FAILED → ROLLBACK"
        });
        return result;
    }

    /**
     * 【重点】演示大 Key 迁移阻塞场景.
     *
     * 面试追问：迁移过程中，某个 key 非常大（如 500MB），会发生什么？
     */
    public Map<String, Object> demonstrateBigKeyMigrationRisk() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", "迁移 500MB 大 Key：analytics:2024 (slot 5000)");

        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(state("MIGRATE_START",
                "MIGRATE target 7001 analytics:2024 0 30000", "开始迁移..."));
        timeline.add(state("BLOCKING",
                "MIGRATE 是同步操作——Redis 主线程在执行期间被阻塞", "⚠ 主线程阻塞中"));
        timeline.add(state("SENTINEL_FALSE_POSITIVE",
                "Sentinel 在 down-after-milliseconds 内未收到 PONG → 误判主节点下线",
                "⚠ Sentinel 误判——可能触发不必要的故障转移"));
        timeline.add(state("SLOW_LOG",
                "SLOWLOG 记录: MIGRATE 耗时 4500ms", "⚠ 远超 10ms 阈值"));
        timeline.add(state("COMPLETED",
                "MIGRATE 完成——主线程恢复", "集群恢复服务，但 P99 延迟尖刺已产生"));

        result.put("timeline", timeline);
        result.put("prevention", new String[]{
                "迁移前强制扫描 --bigkeys，超 100MB 禁止自动迁移",
                "大 Key 强制业务方拆分（按日期/用户尾号分片）",
                "实在无法拆分：用 DUMP + RESTORE 异步迁移，或直接删除重建"
        });
        result.put("lesson", "500MB 的大 Key 就是定时炸弹——平时不注意，迁移时就要命");

        return result;
    }

    // ---- helpers -----------------------------------------------------------

    private Map<String, Object> state(String phase, String action, String result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phase", phase);
        m.put("action", action);
        m.put("result", result);
        m.put("time", LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        return m;
    }

    private Map<String, Object> step(String id, String action, String result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step", id);
        m.put("action", action);
        m.put("result", result);
        return m;
    }

}
