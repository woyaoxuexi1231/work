package com.redis.demo.q4_cross_slot_transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Q4: 跨槽事务——Hash Tag 的诱惑与 SAGA 的务实.
 *
 * <pre>
 * 先启动 Cluster: sudo bash scripts/redis_cluster_start.sh
 * MySQL: mysql -h 192.168.3.100 -u root -p123456 < src/main/resources/init.sql
 * 启动: mvn spring-boot:run -Dspring-boot.run.profiles=cluster
 * </pre>
 */
@RestController
@RequestMapping("/api/q4")
public class CrossSlotController {

    private static final Logger log = LoggerFactory.getLogger(CrossSlotController.class);

    @Value("${spring.redis.cluster.nodes:192.168.3.100:7000,192.168.3.100:7001,192.168.3.100:7002,192.168.3.100:7003,192.168.3.100:7004,192.168.3.100:7005}")
    private String clusterNodes;

    @Value("${spring.redis.password:123456}")
    private String password;

    private final JdbcTemplate db;
    private JedisCluster cluster;
    private Jedis anyNode; // 仅用于 CLUSTER KEYSLOT 计算

    public CrossSlotController(JdbcTemplate db) { this.db = db; }

    @PostConstruct
    public void init() {
        Set<HostAndPort> nodes = new HashSet<>();
        for (String s : clusterNodes.split(",")) {
            String[] p = s.trim().split(":");
            nodes.add(new HostAndPort(p[0], Integer.parseInt(p[1])));
        }
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(8); cfg.setMaxIdle(4);
        this.cluster = new JedisCluster(nodes, 3000, 3000, 3, password, cfg);

        String[] p = clusterNodes.split(",")[0].trim().split(":");
        this.anyNode = new Jedis(p[0], Integer.parseInt(p[1]), 3000);
        this.anyNode.auth(password);

        log.info("JedisCluster 初始化完成: {} nodes", nodes.size());
    }

    // ================================================================
    // 端点 1: 跨槽 Lua → CROSSLOT
    // ================================================================

    @PostMapping("/cross-slot-lua")
    public Map<String, Object> crossSlotLua(@RequestParam String orderId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orderId", orderId);

        String stockKey  = "stock:" + orderId;
        String statusKey = "order:status:" + orderId;
        String userKey   = "user:purchased:" + orderId;

        // 展示三个 key 的 slot
        List<Map<String, Object>> keys = new ArrayList<>();
        for (String k : new String[]{stockKey, statusKey, userKey}) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", k);
            m.put("slot", anyNode.clusterKeySlot(k));
            keys.add(m);
        }
        r.put("keys", keys);

        // 【重点】跨槽 Lua——Cluster 拒绝
        String script =
                "redis.call('SET', KEYS[1], ARGV[1]);" +
                "redis.call('SET', KEYS[2], ARGV[2]);" +
                "redis.call('SADD', KEYS[3], ARGV[3]);" +
                "return 'ok';";

        try {
            cluster.eval(script, 3, stockKey, statusKey, userKey,
                    "99", "PAID", orderId);
            r.put("status", "意外成功——三个 key 居然同槽");
        } catch (Exception e) {
            r.put("status", "CROSSSLOT——被拒绝");
            r.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            r.put("explanation",
                    "Cluster 不支持跨节点 Lua。三个 key 在不同 slot → 拒绝执行。");
        }
        return r;
    }

    // ================================================================
    // 端点 2: Hash Tag → 成功但看到风险
    // ================================================================

    @PostMapping("/hash-tag-lua")
    public Map<String, Object> hashTagLua(@RequestParam String orderId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orderId", orderId);

        String stockKey  = "stock:{" + orderId + "}";
        String statusKey = "order:status:{" + orderId + "}";
        String userKey   = "user:purchased:{" + orderId + "}";

        List<Map<String, Object>> keys = new ArrayList<>();
        long slot = anyNode.clusterKeySlot(stockKey);
        for (String k : new String[]{stockKey, statusKey, userKey}) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", k);
            m.put("slot", anyNode.clusterKeySlot(k));
            keys.add(m);
        }
        r.put("keys", keys);
        r.put("allSameSlot", slot);

        String script =
                "redis.call('SET', KEYS[1], ARGV[1]);" +
                "redis.call('SET', KEYS[2], ARGV[2]);" +
                "redis.call('SADD', KEYS[3], ARGV[3]);" +
                "return 'ok';";

        try {
            Object result = cluster.eval(script, 3,
                    stockKey, statusKey, userKey,
                    "99", "PAID", orderId);
            r.put("luaResult", "✓ 成功——三个 key 在 slot " + slot + "，Lua 原子执行");
        } catch (Exception e) {
            r.put("luaResult", "失败: " + e.getMessage());
        }

        r.put("risks", new LinkedHashMap<String, String>() {{
            put("热点风险", "订单 " + orderId + " 的所有数据集中在一个 slot → 该节点可能被压垮");
            put("数据倾斜", "大量同一 tag 的 key 挤在同一节点，其他节点资源浪费");
            put("扩展性丧失", "无法通过加节点分摊该 slot——slot 是原子迁移最小单位");
        }});
        r.put("verdict", "Hash Tag 是'饮鸩止渴'——短期解决原子性，长期引入热点。"
                + "生产环境推荐 SAGA 补偿。");

        cluster.del(stockKey, statusKey, userKey);
        return r;
    }

    // ================================================================
    // 端点 3 & 4: SAGA 执行 + 日志
    // ================================================================

    @PostMapping("/saga/execute")
    public Map<String, Object> sagaExecute(@RequestParam String orderId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orderId", orderId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        boolean ok = true;

        String lockKey   = "lock:{" + orderId + "}";
        String stockKey  = "stock:{" + orderId + "}";
        String statusKey = "order:status:{" + orderId + "}";
        String userKey   = "user:purchased:{" + orderId + "}";

        // PREPARE
        timeline.add(step("PREPARE", "预占", "SET NX " + lockKey, "RUNNING"));
        sagaLog(orderId, "PREPARE", "RUNNING", "");
        String locked = cluster.set(lockKey, "1",
                new redis.clients.jedis.params.SetParams().nx().ex(60));
        if ("OK".equals(locked)) {
            timeline.add(step("PREPARE", "预占成功", "OK", "SUCCESS"));
            sagaLog(orderId, "PREPARE", "SUCCESS", "");
        } else {
            timeline.add(step("PREPARE", "重复提交", "FAILED", "FAILED"));
            sagaLog(orderId, "PREPARE", "FAILED", "重复提交");
            ok = false;
        }

        // STOCK_CHECK
        if (ok) {
            timeline.add(step("STOCK_CHECK", "扣减库存", "DECR " + stockKey, "RUNNING"));
            sagaLog(orderId, "STOCK_CHECK", "RUNNING", "");
            if (orderId.contains("fail")) {
                timeline.add(step("STOCK_CHECK", "库存不足！", "FAILED", "FAILED"));
                sagaLog(orderId, "STOCK_CHECK", "FAILED", "库存不足");
                // 补偿
                timeline.add(step("COMPENSATE", "【补偿】释放预占", "DEL " + lockKey, "RUNNING"));
                sagaLog(orderId, "COMPENSATE", "RUNNING", "回滚 PREPARE");
                cluster.del(lockKey);
                timeline.add(step("COMPENSATE", "补偿完成", "OK", "COMPENSATED"));
                sagaLog(orderId, "COMPENSATE", "COMPENSATED", "");
                ok = false;
            } else {
                cluster.decr(stockKey);
                timeline.add(step("STOCK_CHECK", "扣减成功", "OK", "SUCCESS"));
                sagaLog(orderId, "STOCK_CHECK", "SUCCESS", "");
            }
        }

        if (ok) {
            cluster.set(statusKey, "PAID");
            timeline.add(step("STATUS_UPDATE", "更新为 PAID", "OK", "SUCCESS"));
            sagaLog(orderId, "STATUS_UPDATE", "SUCCESS", "");

            cluster.sadd(userKey, orderId);
            timeline.add(step("LIST_APPEND", "追加已购列表", "OK", "SUCCESS"));
            sagaLog(orderId, "LIST_APPEND", "SUCCESS", "");

            cluster.del(lockKey, stockKey, statusKey, userKey);
        }

        r.put("success", ok);
        r.put("timeline", timeline);
        r.put("principle", "SAGA = 正向步骤链 + 反向补偿。最终一致，非 XA 强一致。");
        return r;
    }

    @GetMapping("/saga/log")
    public List<Map<String, Object>> sagaLog(@RequestParam String orderId) {
        return db.queryForList(
                "SELECT step, status, error_msg, create_time "
                        + "FROM saga_transaction WHERE order_id = ? ORDER BY id", orderId);
    }

    // ---- helpers ----

    private Map<String, Object> step(String step, String action, String redisCmd, String result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step", step); m.put("action", action);
        m.put("redisCmd", redisCmd); m.put("result", result);
        m.put("time", LocalDateTime.now().toString().substring(11, 19));
        return m;
    }

    private void sagaLog(String orderId, String step, String status, String error) {
        try {
            db.update("INSERT INTO saga_transaction (order_id, step, status, error_msg) "
                    + "VALUES (?, ?, ?, ?)", orderId, step, status,
                    error.isEmpty() ? null : error);
        } catch (Exception e) {
            log.warn("SAGA 日志写入失败(MySQL 不可达): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        if (cluster != null) cluster.close();
        if (anyNode != null) anyNode.close();
    }
}
