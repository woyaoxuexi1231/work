package com.redis.demo.q3_cluster_slot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Q3: Redis Cluster 分片路由——CRC16、槽位、MOVED/ASK.
 *
 * <pre>
 * 先启动 Cluster: sudo bash scripts/redis_cluster_start.sh
 * 启动 Spring Boot: mvn spring-boot:run -Dspring-boot.run.profiles=cluster
 *
 * # 1. 手算 slot 并和 Redis 对比
 * curl 'http://localhost:8080/api/q3/slot?key=order:123'
 *
 * # 2. 看集群槽位分布
 * curl http://localhost:8080/api/q3/topology
 *
 * # 3. 故意发到错误节点，看 MOVED
 * curl -X POST 'http://localhost:8080/api/q3/moved-demo?key=testkey'
 *
 * # 4. 为什么是 16384
 * curl http://localhost:8080/api/q3/why-16384
 * </pre>
 */
@RestController
@RequestMapping("/api/q3")
public class SlotController {

    @Value("${spring.redis.cluster.nodes:192.168.3.100:7000,192.168.3.100:7001,192.168.3.100:7002,192.168.3.100:7003,192.168.3.100:7004,192.168.3.100:7005}")
    private String clusterNodes;

    @Value("${spring.redis.password:123456}")
    private String password;

    /** 连接任意节点用的 Jedis */
    private Jedis anyNode;

    @PostConstruct
    public void init() {
        String[] parts = clusterNodes.split(",")[0].trim().split(":");
        anyNode = new Jedis(parts[0], Integer.parseInt(parts[1]), 3000);
        anyNode.auth(password);
    }

    // ---- CRC-16-CCITT 查找表（与 Redis 源码 crc16.c 完全一致） ----
    private static final int[] CRC16_TABLE = new int[256];
    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int j = 0; j < 8; j++)
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
            CRC16_TABLE[i] = crc & 0xFFFF;
        }
    }

    // ================================================================
    // 端点 1: 手算 slot + Redis 验证
    // ================================================================

    /**
     * 输入任意 key，返回 Java 手算的 CRC16 → slot，
     * 以及 Redis CLUSTER KEYSLOT 的结果，两者应该一致。
     */
    @GetMapping("/slot")
    public Map<String, Object> slot(@RequestParam String key) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("key", key);

        // 【重点】Hash Tag 提取：{} 之间的内容参与哈希
        String hashTarget = extractHashTag(key);
        r.put("hashTarget", hashTarget.equals(key) ? "(整 key)" : hashTarget);

        // Java 手算
        int crc16 = crc16(hashTarget.getBytes());
        int slot  = crc16 & 0x3FFF; // 【重点】低 14 位 → 0-16383
        r.put("javaCrc16", String.format("0x%04X", crc16));
        r.put("javaSlot", slot);

        // 【重点】Redis 验证——CLUSTER KEYSLOT
        try {
            long redisSlot = anyNode.clusterKeySlot(key);
            r.put("redisSlot", redisSlot);
            r.put("match", slot == redisSlot ? "✓ 一致" : "✗ 不一致！");
        } catch (Exception e) {
            r.put("redisError", e.getMessage());
        }

        return r;
    }

    // ================================================================
    // 端点 2: 集群拓扑——哪个节点管哪些槽
    // ================================================================

    @GetMapping("/topology")
    public Map<String, Object> topology() {
        Map<String, Object> r = new LinkedHashMap<>();

        try {
            List<Object> slots = anyNode.clusterSlots(); // CLUSTER SLOTS
            List<Map<String, Object>> nodes = new ArrayList<>();
            int total = 0;

            for (Object obj : slots) {
                List<Object> slotInfo = (List<Object>) obj;
                long start = (Long) slotInfo.get(0);
                long end   = (Long) slotInfo.get(1);
                int count  = (int) (end - start + 1);
                total += count;

                List<Object> master = (List<Object>) slotInfo.get(2);
                String nodeIp   = new String((byte[]) master.get(0));
                long   nodePort = (Long) master.get(1);

                Map<String, Object> node = new LinkedHashMap<>();
                node.put("range", start + "-" + end);
                node.put("count", count);
                node.put("node", nodeIp + ":" + nodePort);
                nodes.add(node);
            }

            r.put("nodes", nodes);
            r.put("totalSlots", total);
            r.put("totalNodes", nodes.size());

        } catch (Exception e) {
            r.put("error", "集群不可达: " + e.getMessage());
        }
        return r;
    }

    // ================================================================
    // 端点 3: MOVED 重定向演示——故意发错节点
    // ================================================================

    /**
     * 用 CLUSTER KEYSLOT 算出 slot，找到负责该 slot 的节点 A。
     * 然后故意把 GET 发到节点 B（不负责该 slot 的节点），
     * 观察 Redis 返回的 MOVED 错误。
     */
    @PostMapping("/moved-demo")
    public Map<String, Object> movedDemo(@RequestParam(defaultValue = "testkey") String key) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("key", key);

        // 算出 slot 和正确节点
        long slot = anyNode.clusterKeySlot(key);
        r.put("slot", slot);

        // 找到负责该 slot 的节点
        List<Object> allSlots = anyNode.clusterSlots();
        String correctNode = null;
        for (Object obj : allSlots) {
            List<Object> info = (List<Object>) obj;
            long start = (Long) info.get(0);
            long end   = (Long) info.get(1);
            if (slot >= start && slot <= end) {
                List<Object> master = (List<Object>) info.get(2);
                correctNode = new String((byte[]) master.get(0)) + ":" + master.get(1);
                break;
            }
        }
        r.put("correctNode", correctNode);

        // 找一个不负责该 slot 的节点
        String wrongNode = null;
        for (String node : clusterNodes.split(",")) {
            String n = node.trim().replace("192.168.3.100", new String((byte[]) ((List<Object>)
                    ((List<Object>) anyNode.clusterSlots().get(0)).get(2)).get(0)));
            if (!node.trim().equals(correctNode) && !node.trim().startsWith(
                    correctNode == null ? "" : correctNode.split(":")[0])) {
                wrongNode = node.trim();
                break;
            }
        }
        if (wrongNode == null) wrongNode = clusterNodes.split(",")[1].trim();

        r.put("sentTo", wrongNode + "（故意发到错误节点）");

        // 【重点】向错误节点发 GET——看 MOVED
        String[] wp = wrongNode.split(":");
        try (Jedis wrong = new Jedis(wp[0], Integer.parseInt(wp[1]), 2000)) {
            wrong.auth(password);
            wrong.get(key); // 这会抛异常
            r.put("result", "意外——居然没报错");
        } catch (Exception e) {
            String msg = e.getMessage();
            r.put("MOVED_response", msg);
            r.put("type", msg != null && msg.contains("MOVED") ? "MOVED（永久重定向）" : "其他错误");

            // 提取 MOVED 中的目标节点
            if (msg != null && msg.contains("MOVED")) {
                r.put("explanation", "Redis 说: '槽 " + slot + " 归 " + correctNode
                        + " 管，你去那里。请更新你的槽位映射表，下次直接来找我。'");
                r.put("vs_ASK", "MOVED 是永久重定向——客户端必须更新本地槽表。"
                        + "ASK 是临时重定向——槽正在迁移中，客户端不更新槽表，但要带 ASKING 命令。");
            }
        }

        return r;
    }

    // ================================================================
    // 端点 4: 为什么是 16384
    // ================================================================

    @GetMapping("/why-16384")
    public Map<String, Object> why16384() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalSlots", 16384);
        r.put("reasons", new LinkedHashMap<String, String>() {{
            put("心跳消息大小",
                    "节点间 Gossip 协议的心跳包包含一个位图表示本节点负责的槽。"
                            + "16384 bits = 2048 bytes = 2KB——正好适配以太网标准帧，网络开销极低。");
            put("CRC16 输出空间",
                    "CRC-16-CCITT 输出 16 位（0-65535）。"
                            + "16384 = 2^14，恰好取低 14 位（& 0x3FFF）。"
                            + "如果取 65536 个槽，心跳包会膨胀到 8KB，Gossip 消息开销翻 4 倍。");
            put("节点扩展粒度",
                    "扩展到 1000 个节点时，每个节点平均 16.4 个槽，"
                            + "迁移粒度足够细——搬一个槽不会导致整台机器负载巨变。"
                            + "8192 个槽在 1000 节点时每个仅 8 个槽，粒度太粗。");
            put("源码验证",
                    "Redis 源码 cluster.h: #define CLUSTER_SLOTS 16384");
        }});

        r.put("comparison", new LinkedHashMap<String, Object>() {{
            put("如果 8192", "心跳包 1KB 更小，但 1000 节点时每节点 8 槽——迁移粒度粗糙");
            put("如果 65536", "粒度极细，但心跳包 8KB——1000 节点集群 Gossip 消息洪流");
            put("16384（实际）", "2KB 心跳包 + 千节点 16 槽——工程上的黄金平衡点");
        }});

        return r;
    }

    // ================================================================
    // 端点 5: Hash Tag 演示
    // ================================================================

    @GetMapping("/hash-tag")
    public Map<String, Object> hashTag(@RequestParam(defaultValue = "order:123") String tag) {
        Map<String, Object> r = new LinkedHashMap<>();

        List<Map<String, String>> without = new ArrayList<>();
        for (String key : new String[]{"inventory:" + tag, "order:status:" + tag, "user:purchased:" + tag}) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("slot", String.valueOf(anyNode.clusterKeySlot(key)));
            without.add(m);
        }
        r.put("withoutHashTag", without);

        List<Map<String, String>> with = new ArrayList<>();
        for (String key : new String[]{"inventory:{" + tag + "}", "order:status:{" + tag + "}", "user:purchased:{" + tag + "}"}) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("slot", String.valueOf(anyNode.clusterKeySlot(key)));
            with.add(m);
        }
        r.put("withHashTag", with);

        Set<String> slotsWithout = new HashSet<>();
        Set<String> slotsWith = new HashSet<>();
        for (Map<String, String> m : without) slotsWithout.add(m.get("slot"));
        for (Map<String, String> m : with) slotsWith.add(m.get("slot"));

        r.put("conclusion", "无 Hash Tag → " + slotsWithout.size() + " 个不同 slot；"
                + "有 Hash Tag → 全部 slot " + slotsWith + "。"
                + "Hash Tag 解决了跨槽原子性问题，但引入了数据倾斜风险——"
                + "所有带同一 tag 的 key 挤在同一节点，可能成为热点。");

        return r;
    }

    // ---- helpers ----

    private String extractHashTag(String key) {
        int s = key.indexOf('{'), e = key.indexOf('}', s + 1);
        return (s >= 0 && e > s + 1) ? key.substring(s + 1, e) : key;
    }

    private int crc16(byte[] bytes) {
        int crc = 0;
        for (byte b : bytes)
            crc = ((crc << 8) ^ CRC16_TABLE[((crc >>> 8) ^ (b & 0xFF)) & 0xFF]) & 0xFFFF;
        return crc;
    }
}
