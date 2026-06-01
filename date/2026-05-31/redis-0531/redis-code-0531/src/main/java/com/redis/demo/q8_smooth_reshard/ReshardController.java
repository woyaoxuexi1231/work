package com.redis.demo.q8_smooth_reshard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * Q8: 平滑扩缩容 —— 真实 Cluster reshard 监控.
 *
 * <h3>不模拟，只监控。</h3>
 * 真正的 reshard 由 redis-cli --cluster reshard 执行。
 * 此控制器帮你：
 * <ol>
 *   <li>查看当前集群 slot 分布</li>
 *   <li>找出 slot 分布不均衡的节点</li>
 *   <li>监控 MIGRATE 进度</li>
 *   <li>记录迁移状态到 MySQL</li>
 * </ol>
 *
 * <h3>演练步骤</h3>
 * <ol>
 *   <li>GET /api/ops/reshard/slot-distribution → 看当前分布</li>
 *   <li>新节点加入集群: redis-cli --cluster add-node ...</li>
 *   <li>redis-cli --cluster rebalance ... → 开始了</li>
 *   <li>循环 GET /api/ops/reshard/slot-distribution → 看槽位迁移</li>
 *   <li>GET /api/ops/reshard/migrating-slots → 看哪些槽正在搬</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/ops/reshard")
public class ReshardController {

    private static final Logger log = LoggerFactory.getLogger(ReshardController.class);

    @Value("${spring.redis.cluster.nodes:192.168.3.100:7000,192.168.3.100:7001,192.168.3.100:7002,192.168.3.100:7003,192.168.3.100:7004,192.168.3.100:7005}")
    private String clusterNodes;

    @Value("${spring.redis.password:123456}")
    private String password;

    private final JdbcTemplate jdbcTemplate;

    public ReshardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 【核心】查看当前集群 slot 分布.
     *
     * 连接任意一个 Cluster 节点，执行 CLUSTER SLOTS，
     * 统计每个节点的 slot 数量，识别不均衡。
     */
    @GetMapping("/slot-distribution")
    public Map<String, Object> slotDistribution() {
        Map<String, Object> result = new LinkedHashMap<>();

        String[] parts = clusterNodes.split(",")[0].trim().split(":");
        try (Jedis jedis = new Jedis(parts[0], Integer.parseInt(parts[1]), 3000)) {
            jedis.auth(password);

            List<Object> slots = jedis.clusterSlots();
            Map<String, NodeSlotInfo> nodeMap = new LinkedHashMap<>();
            int totalSlots = 0;

            for (Object obj : slots) {
                List<Object> slotInfo = (List<Object>) obj;
                long start = (Long) slotInfo.get(0);
                long end = (Long) slotInfo.get(1);
                int count = (int) (end - start + 1);
                totalSlots += count;

                // 主节点
                List<Object> masterInfo = (List<Object>) slotInfo.get(2);
                String masterId = new String((byte[]) masterInfo.get(0));

                nodeMap.computeIfAbsent(masterId,
                        k -> new NodeSlotInfo()).addSlots(start, end, count);
            }

            List<Map<String, Object>> nodes = new ArrayList<>();
            for (Map.Entry<String, NodeSlotInfo> entry : nodeMap.entrySet()) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("nodeId", entry.getKey());
                node.put("slotCount", entry.getValue().slotCount);
                node.put("slotRanges", entry.getValue().ranges);
                nodes.add(node);
            }
            nodes.sort(Comparator.comparingInt(n -> (int) n.get("slotCount")));

            result.put("totalSlots", totalSlots);
            result.put("nodeCount", nodes.size());
            result.put("nodes", nodes);

            // 均衡度评估
            if (!nodes.isEmpty()) {
                int avg = totalSlots / nodes.size();
                int min = (int) nodes.get(0).get("slotCount");
                int max = (int) nodes.get(nodes.size() - 1).get("slotCount");
                result.put("avgSlotsPerNode", avg);
                result.put("minSlots", min);
                result.put("maxSlots", max);
                result.put("imbalance", max - min);
                result.put("balanced", (max - min) <= avg * 0.1);
                if (!result.get("balanced").equals(true)) {
                    result.put("suggestion",
                            "不均衡——执行: redis-cli --cluster rebalance "
                                    + parts[0] + ":" + parts[1] + " --cluster-password "
                                    + password);
                }
            }

        } catch (Exception e) {
            result.put("error", "无法连接集群: " + e.getMessage());
        }

        return result;
    }

    /**
     * 【重点】查找正在迁移的 slot（状态为 MIGRATING 或 IMPORTING）.
     *
     * 迁移期间，源节点上该 slot 显示 "MIGRATING"，
     * 目标节点显示 "IMPORTING"。
     */
    @GetMapping("/migrating-slots")
    public Map<String, Object> migratingSlots() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> migrating = new ArrayList<>();
        List<Map<String, Object>> importing = new ArrayList<>();

        for (String nodeStr : clusterNodes.split(",")) {
            String[] parts = nodeStr.trim().split(":");
            try (Jedis jedis = new Jedis(parts[0], Integer.parseInt(parts[1]), 3000)) {
                jedis.auth(password);
                String nodesInfo = jedis.clusterNodes();

                for (String line : nodesInfo.split("\n")) {
                    if (line.contains("myself")) {
                        if (line.contains("[") && line.contains("->-")) {
                            // 有 migrating slot
                            Map<String, Object> m = parseMigratingSlot(line, "MIGRATING");
                            if (m != null) migrating.add(m);
                        }
                        if (line.contains("-<-")) {
                            Map<String, Object> m = parseMigratingSlot(line, "IMPORTING");
                            if (m != null) importing.add(m);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("节点 {} 不可达: {}", nodeStr, e.getMessage());
            }
        }

        result.put("migrating", migrating);
        result.put("importing", importing);
        result.put("activeMigrations", migrating.size() + importing.size());
        result.put("note", migrating.isEmpty()
                ? "当前无活跃迁移——集群稳定"
                : "⚠ 有 " + migrating.size() + " 个槽正在迁移中");

        return result;
    }

    /**
     * 查看 CLUSTER NODES 原始输出（最重要——面试时要能读这个）.
     */
    @GetMapping("/cluster-nodes")
    public Map<String, Object> clusterNodesRaw() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> nodeOutputs = new LinkedHashMap<>();

        for (String nodeStr : clusterNodes.split(",")) {
            String[] parts = nodeStr.trim().split(":");
            try (Jedis jedis = new Jedis(parts[0], Integer.parseInt(parts[1]), 3000)) {
                jedis.auth(password);
                nodeOutputs.put(nodeStr.trim(), jedis.clusterNodes());
            } catch (Exception e) {
                nodeOutputs.put(nodeStr.trim(), "ERROR: " + e.getMessage());
            }
        }

        result.put("nodesOutput", nodeOutputs);
        result.put("howToRead", new LinkedHashMap<String, String>() {{
            put("格式", "nodeId ip:port@busPort flags masterId ping-sent pong-recv epoch link-state [slots]");
            put("flags", "myself,master / slave / fail? / handshake / noaddr / noflags");
            put("slots格式", "0-5460 或 [5461-<-abc123]（IMPORTING）或 [5461->-def456]（MIGRATING）");
        }});

        return result;
    }

    /**
     * 大 Key 检查命令——示范如何在实际环境中检测.
     */
    @GetMapping("/bigkey-check")
    public Map<String, Object> bigKeyCheck() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "大 Key 检测——迁移前的必修课");

        // 对每个节点给出检测命令
        List<Map<String, String>> commands = new ArrayList<>();
        for (String nodeStr : clusterNodes.split(",")) {
            String[] parts = nodeStr.trim().split(":");
            Map<String, String> cmd = new LinkedHashMap<>();
            cmd.put("node", nodeStr.trim());
            cmd.put("bigkeysCmd",
                    "redis-cli -h " + parts[0] + " -p " + parts[1]
                            + " -a " + password + " --bigkeys");
            cmd.put("memkeysCmd",
                    "redis-cli -h " + parts[0] + " -p " + parts[1]
                            + " -a " + password + " MEMORY STATS");
            commands.add(cmd);
        }
        result.put("perNodeCommands", commands);

        result.put("migrateRisk", new String[]{
                "⚠ 迁移前必须扫描 --bigkeys！",
                "超 100MB 的 key 迁移时会阻塞主线程",
                "超 500MB 的 key → MIGRATE 持续数秒 → Sentinel 可能误判",
                "对策：拆分大 Key 或 DUMP+RESTORE 异步迁移"
        });

        return result;
    }

    /**
     * 迁移进度持久化状态（从 MySQL 读取）.
     */
    @GetMapping("/migration-log")
    public Map<String, Object> migrationLog() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                    "SELECT slot, source_node, target_node, state, key_count, update_time "
                            + "FROM reshard_task ORDER BY update_time DESC LIMIT 30");
            result.put("tasks", tasks);
            result.put("count", tasks.size());
        } catch (Exception e) {
            result.put("error", "MySQL 不可达: " + e.getMessage());
        }
        return result;
    }

    // ---- helpers -----------------------------------------------------------

    private static class NodeSlotInfo {
        int slotCount;
        List<String> ranges = new ArrayList<>();

        void addSlots(long start, long end, int count) {
            slotCount += count;
            ranges.add(start + "-" + end + " (" + count + " slots)");
        }
    }

    private Map<String, Object> parseMigratingSlot(String line, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        // 提取 slot 范围
        int bracketStart = line.indexOf('[');
        int bracketEnd = line.indexOf(']', bracketStart);
        if (bracketStart >= 0 && bracketEnd > bracketStart) {
            m.put("slotInfo", line.substring(bracketStart, bracketEnd + 1));
        }
        // 提取节点 ID（第一个空格前的内容是节点自身 ID）
        String[] parts = line.split("\\s+");
        if (parts.length > 0) m.put("nodeId", parts[0]);
        if (parts.length > 1) m.put("address", parts[1]);
        return m;
    }
}
