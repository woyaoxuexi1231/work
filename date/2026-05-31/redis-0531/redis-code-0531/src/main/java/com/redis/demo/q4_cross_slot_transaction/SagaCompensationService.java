package com.redis.demo.q4_cross_slot_transaction;

import com.redis.demo.q3_cluster_slot.SlotCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Q4: 跨槽事务与 SAGA 补偿 —— 分布式事务的务实方案.
 *
 * <h3>场景</h3>
 * 一笔电商订单需要原子操作三个 Redis Key：
 * <ol>
 *   <li>stock:phone-123 —— 库存扣减</li>
 *   <li>order:status:456 —— 订单状态更新</li>
 *   <li>user:purchased:789 —— 用户已购列表追加</li>
 * </ol>
 * 在 Cluster 模式下，这三个 key 大概率不在同一 slot → Redis Cluster 拒绝执行。
 *
 * <h3>三种方案对比</h3>
 * <table>
 *   <tr><th>方案</th><th>优点</th><th>致命缺陷</th></tr>
 *   <tr><td>Hash Tag</td><td>原子性保证</td><td>数据倾斜、热点</td></tr>
 *   <tr><td>SAGA</td><td>可扩展、无热点</td><td>最终一致</td></tr>
 *   <tr><td>Redlock</td><td>分布式锁</td><td>Cluster 下不推荐，性能差</td></tr>
 * </table>
 *
 * <h3>对应面试题 Q4</h3>
 * <ul>
 *   <li>跨槽 Lua 为什么被拒？→ CROSSSLOT error</li>
 *   <li>Hash Tag 的风险？→ 热点 + 数据倾斜</li>
 *   <li>SAGA 如何补偿？→ 正向步骤 + 反向补偿</li>
 * </ul>
 */
@Service
public class SagaCompensationService {

    private static final Logger log = LoggerFactory.getLogger(SagaCompensationService.class);

    private final SlotCalculator slotCalculator;
    private final JdbcTemplate jdbcTemplate;

    public SagaCompensationService(SlotCalculator slotCalculator,
                                   JdbcTemplate jdbcTemplate) {
        this.slotCalculator = slotCalculator;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ========================================================================
    // 跨槽 Lua 被拒演示
    // ========================================================================

    /**
     * 【重点】模拟跨槽 Lua 脚本被 Redis Cluster 拒绝.
     *
     * 真实 Redis Cluster 返回：
     *   CROSSSLOT Keys in request don't hash to the same slot
     *
     * 这段代码让你亲眼看到三个真实业务 key 落到不同的 slot 上，
     * 从而理解为什么 Cluster 下 Lua 脚本不能跨槽。
     */
    public Map<String, Object> simulateCrossSlotRejection(String orderId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);

        // 三个业务 key
        String stockKey   = "stock:" + orderId;
        String statusKey  = "order:status:" + orderId;
        String userKey    = "user:purchased:" + orderId;

        // 计算各自的 slot
        int stockSlot  = slotCalculator.slotForKey(stockKey);
        int statusSlot = slotCalculator.slotForKey(statusKey);
        int userSlot   = slotCalculator.slotForKey(userKey);

        List<Map<String, Object>> keys = new ArrayList<>();
        keys.add(keyInfo(stockKey, stockSlot));
        keys.add(keyInfo(statusKey, statusSlot));
        keys.add(keyInfo(userKey, userSlot));
        result.put("keys", keys);

        // 判断是否同槽
        boolean sameSlot = (stockSlot == statusSlot && statusSlot == userSlot);
        result.put("sameSlot", sameSlot);

        // Lua 脚本
        String luaScript =
                "-- 跨槽 Lua 脚本（在 Cluster 下会失败）\n" +
                "redis.call('DECRBY', KEYS[1], ARGV[1])           -- 扣库存\n" +
                "redis.call('SET', KEYS[2], ARGV[2])              -- 改订单状态\n" +
                "redis.call('SADD', KEYS[3], ARGV[3])             -- 加已购列表\n" +
                "return 'ok'";
        result.put("luaScript", luaScript);

        if (sameSlot) {
            result.put("result", "✓ 三个 key 恰好在同一 slot，Lua 脚本可以执行");
            result.put("note", "概率极低！正常业务 key 基本不可能恰好同槽");
        } else {
            result.put("result", "✗ CROSSSLOT Keys in request don't hash to the same slot");
            result.put("slots", stockSlot + ", " + statusSlot + ", " + userSlot);
            result.put("solutions", new String[]{
                    "方案A: Hash Tag —— {orderId} 强制同槽（有热点风险）",
                    "方案B: SAGA 长事务补偿（推荐，见 /api/cluster/transaction/saga/execute）",
                    "方案C: 放弃 Redis 事务，用数据库保证强一致（Redis 只做缓存）"
            });
        }
        return result;
    }

    /**
     * 【重点】演示 Hash Tag 强制同槽的效果与风险.
     */
    public Map<String, Object> demonstrateHashTagSolution(String orderId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);

        // 加 Hash Tag 的三个 key
        String stockKey  = "stock:{" + orderId + "}";
        String statusKey = "order:status:{" + orderId + "}";
        String userKey   = "user:purchased:{" + orderId + "}";

        int stockSlot  = slotCalculator.slotForKey(stockKey);
        int statusSlot = slotCalculator.slotForKey(statusKey);
        int userSlot   = slotCalculator.slotForKey(userKey);

        List<Map<String, Object>> keys = new ArrayList<>();
        keys.add(keyInfo(stockKey, stockSlot));
        keys.add(keyInfo(statusKey, statusSlot));
        keys.add(keyInfo(userKey, userSlot));
        result.put("keys", keys);

        boolean sameSlot = (stockSlot == statusSlot && statusSlot == userSlot);
        result.put("sameSlot", sameSlot);
        result.put("slot", stockSlot);

        if (sameSlot) {
            result.put("luaResult", "✓ 可以执行！三个 key 都落在 slot " + stockSlot);
        }

        //【重点】风险分析
        result.put("risks", new LinkedHashMap<String, String>() {{
            put("热点风险",
                    "订单 " + orderId + " 的所有数据集中在一个 slot → "
                            + "该 slot 所在节点可能被压垮");
            put("数据倾斜",
                    "大量使用同一 tag 的业务 key 全部挤在同一节点，"
                            + "集群其它节点资源浪费");
            put("扩展性丧失",
                    "无法通过增加节点来分摊该 slot 的压力——"
                            + "槽是原子迁移的最小单位");
            put("业务耦合",
                    "所有需要原子操作的 key 必须在命名时就规划好 Hash Tag，"
                            + "后期改造成本极高");
        }});

        result.put("verdict", "Hash Tag 是'饮鸩止渴'——短期解决问题，"
                + "长期引入热点。生产环境推荐 SAGA 补偿（见下方接口）");
        return result;
    }

    // ========================================================================
    // SAGA 补偿状态机
    // ========================================================================

    /**
     * 【重点】SAGA 分布式事务——正向步骤 + 反向补偿.
     *
     * 流程：
     *   正向：PREPARE → STOCK_CHECK → STATUS_UPDATE → LIST_APPEND
     *   失败：执行对应步骤的补偿操作（反向回滚）
     *
     * 每个步骤成功/失败都会写入 MySQL（saga_transaction 表），
     * 通过 HTTP 接口可以实时查看状态流转。
     */
    public Map<String, Object> executeSagaTransaction(String orderId) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        boolean overallSuccess = true;

        // Step 1: PREPARE —— 预占
        timeline.add(sagaStep("PREPARE", "预占订单号 " + orderId,
                "SET NX order:lock:" + orderId, "RUNNING"));
        insertSagaLog(orderId, "PREPARE", "RUNNING",
                "{\"orderId\":\"" + orderId + "\"}");
        timeline.add(sagaStep("PREPARE", "预占成功",
                "订单号已锁定，防止重复提交", "SUCCESS"));
        updateSagaStatus(orderId, "PREPARE", "SUCCESS");

        // Step 2: STOCK_CHECK —— 库存扣减（模拟 Redis DECR）
        timeline.add(sagaStep("STOCK_CHECK", "扣减库存 stock:{" + orderId + "}",
                "DECRBY stock:{" + orderId + "} 1", "RUNNING"));
        insertSagaLog(orderId, "STOCK_CHECK", "RUNNING",
                "{\"sku\":\"phone-123\",\"amount\":1}");

        //【重点】模拟可能失败的场景：如果 orderId 包含 "fail" 则模拟失败
        if (orderId.contains("fail")) {
            timeline.add(sagaStep("STOCK_CHECK", "库存扣减失败！库存不足",
                    "DECRBY → 返回 -1", "FAILED"));
            updateSagaStatus(orderId, "STOCK_CHECK", "FAILED", "库存不足");

            //【重点】触发补偿：回滚 PREPARE 步骤
            timeline.add(sagaStep("COMPENSATE", "【补偿回滚】释放预占",
                    "DEL order:lock:" + orderId, "RUNNING"));
            insertSagaLog(orderId, "COMPENSATE", "RUNNING",
                    "{\"rollback\":\"PREPARE\"}");
            timeline.add(sagaStep("COMPENSATE", "补偿完成——订单已取消",
                    "所有已执行步骤已回滚", "COMPENSATED"));
            updateSagaStatus(orderId, "COMPENSATE", "COMPENSATED", "SAGA 补偿完成");
            overallSuccess = false;
        } else {
            timeline.add(sagaStep("STOCK_CHECK", "库存扣减成功",
                    "DECRBY → 剩余库存 99", "SUCCESS"));
            updateSagaStatus(orderId, "STOCK_CHECK", "SUCCESS");
        }

        // Step 3 & 4: 只有前面成功才继续
        if (overallSuccess) {
            // STATUS_UPDATE
            timeline.add(sagaStep("STATUS_UPDATE", "更新订单状态",
                    "SET order:status:{" + orderId + "} PAID", "RUNNING"));
            insertSagaLog(orderId, "STATUS_UPDATE", "RUNNING",
                    "{\"status\":\"PAID\"}");
            timeline.add(sagaStep("STATUS_UPDATE", "订单状态已更新为 PAID",
                    "SET → OK", "SUCCESS"));
            updateSagaStatus(orderId, "STATUS_UPDATE", "SUCCESS");

            // LIST_APPEND
            timeline.add(sagaStep("LIST_APPEND", "追加用户已购列表",
                    "SADD user:purchased:{" + orderId + "} " + orderId, "RUNNING"));
            insertSagaLog(orderId, "LIST_APPEND", "RUNNING",
                    "{\"orderId\":\"" + orderId + "\"}");
            timeline.add(sagaStep("LIST_APPEND", "已购列表更新完成",
                    "SADD → OK", "SUCCESS"));
            updateSagaStatus(orderId, "LIST_APPEND", "SUCCESS");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "SAGA 分布式事务执行"
                + (overallSuccess ? "——全部成功" : "——触发补偿回滚"));
        result.put("orderId", orderId);
        result.put("overallSuccess", overallSuccess);
        result.put("timeline", timeline);
        result.put("principle", "SAGA = 正向步骤链 + 每个步骤的反向补偿操作。"
                + "不同于 XA 两阶段提交，SAGA 是最终一致性方案——"
                + "中间态对外可见，但最终一定会到达一致状态");
        result.put("compensationStrategy",
                "如果 STEP_N 失败 → 依次执行 STEP_{N-1}...STEP_1 的补偿操作");
        return result;
    }

    /**
     * 查看某条订单的 SAGA 事务日志.
     */
    public List<Map<String, Object>> getSagaLog(String orderId) {
        return jdbcTemplate.queryForList(
                "SELECT step, status, error_msg, create_time, update_time "
                        + "FROM saga_transaction WHERE order_id = ? ORDER BY id",
                orderId);
    }

    /**
     * 查看最近 N 条 SAGA 事务.
     */
    public List<Map<String, Object>> getRecentSagas(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT order_id, step, status, error_msg, create_time "
                        + "FROM saga_transaction ORDER BY id DESC LIMIT ?", limit);
    }

    // ---- helpers -----------------------------------------------------------

    private Map<String, Object> keyInfo(String key, int slot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("slot", slot);
        m.put("hashTag", slotCalculator.extractHashTag(key));
        m.put("crc16", String.format("0x%04X", slotCalculator.crc16(
                slotCalculator.extractHashTag(key).getBytes())));
        return m;
    }

    private Map<String, Object> sagaStep(String step, String action,
                                         String redisCmd, String result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step", step);
        m.put("action", action);
        m.put("redisCommand", redisCmd);
        m.put("result", result);
        m.put("time", LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        return m;
    }

    private void insertSagaLog(String orderId, String step,
                               String status, String payload) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO saga_transaction (order_id, step, status, payload) "
                            + "VALUES (?, ?, ?, ?)",
                    orderId, step, status, payload);
        } catch (Exception e) {
            log.warn("SAGA 日志写入失败（MySQL 可能未就绪）: {}", e.getMessage());
        }
    }

    private void updateSagaStatus(String orderId, String step,
                                  String status, String errorMsg) {
        try {
            jdbcTemplate.update(
                    "UPDATE saga_transaction SET status = ?, error_msg = ? "
                            + "WHERE order_id = ? AND step = ?",
                    status, errorMsg, orderId, step);
        } catch (Exception e) {
            log.warn("SAGA 状态更新失败: {}", e.getMessage());
        }
    }

    private void updateSagaStatus(String orderId, String step, String status) {
        updateSagaStatus(orderId, step, status, null);
    }
}
