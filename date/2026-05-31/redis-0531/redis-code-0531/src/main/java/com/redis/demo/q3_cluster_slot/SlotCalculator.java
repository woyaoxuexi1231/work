package com.redis.demo.q3_cluster_slot;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Q3: Redis Cluster 哈希槽计算器 —— 16384 的数学之美.
 *
 * <h3>为什么是 16384？</h3>
 * <ul>
 *   <li>心跳消息大小：每个节点间 gossip 协议的心跳包包含一个位图（bitmap）
 *       来表示该节点负责的槽范围。16384 bits = 2048 bytes = 2KB，正好一个以太网
 *       标准帧大小以内，网络开销极低。</li>
 *   <li>节点扩展上限：即使扩展到 1000 个节点，每个节点平均 16.4 个槽，
 *       迁移粒度足够细，不会出现"搬一块地整台机器宕机"的问题。</li>
 *   <li>CRC16 输出空间：CRC-16-CCITT 输出 16 位，最大 65535。
 *       16384 = 2^14，恰好把 16 位空间对半折，取低 14 位。</li>
 * </ul>
 *
 * <h3>对应面试题 Q3</h3>
 * <ul>
 *   <li>MOVED vs ASK 的本质区别</li>
 *   <li>客户端 Smart Routing 如何维护槽位映射表</li>
 *   <li>Hash Tag 强制同槽的机制</li>
 * </ul>
 */
@Service
public class SlotCalculator {

    // CRC-16-CCITT 查找表 (XMODEM), 与 Redis 源码中 crc16.c 完全一致
    private static final int[] CRC16_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int j = 0; j < 8; j++) {
                crc = (crc & 0x8000) != 0
                        ? (crc << 1) ^ 0x1021
                        : (crc << 1);
            }
            CRC16_TABLE[i] = crc & 0xFFFF;
        }
    }

    /** 模拟 6 个集群节点，每个负责一段槽 */
    private static final int TOTAL_SLOTS = 16384;
    private final Map<String, int[]> nodeSlots = new LinkedHashMap<>();

    public SlotCalculator() {
        // 标准 6 节点槽位分配（与面试题中 7000-7005 对应）
        // 16384 / 6 ≈ 2730.67，前 5 个节点各 2730，最后一个 2734
        nodeSlots.put("7000 (192.168.3.100:7000)", new int[]{0, 2729});
        nodeSlots.put("7001 (192.168.3.100:7001)", new int[]{2730, 5459});
        nodeSlots.put("7002 (192.168.3.100:7002)", new int[]{5460, 8189});
        nodeSlots.put("7003 (192.168.3.100:7003)", new int[]{8190, 10919});
        nodeSlots.put("7004 (192.168.3.100:7004)", new int[]{10920, 13649});
        nodeSlots.put("7005 (192.168.3.100:7005)", new int[]{13650, 16383});
    }

    // ========================================================================
    // CRC16 槽位计算
    // ========================================================================

    /**
     * 【重点】计算 key 对应的 slot —— 这正是 Redis 源码中 keyHashSlot() 做的事.
     *
     * Redis Cluster 的哈希算法：
     * 1. 如果 key 中包含 {...}，只计算 {} 之间的部分（Hash Tag）
     * 2. 对提取出的部分做 CRC-16-CCITT
     * 3. 取低 14 位（& 0x3FFF），得到 0-16383 之间的 slot 号
     */
    public int slotForKey(String key) {
        String hashKey = extractHashTag(key); //【重点】第一步：提取 Hash Tag
        int crc = crc16(hashKey.getBytes());
        return crc & 0x3FFF; //【重点】低 14 位 → 0-16383
    }

    /**
     * 【重点】CRC-16-CCITT 实现 —— 与 Redis 源码 crc16.c 逐位对齐.
     */
    public int crc16(byte[] bytes) {
        int crc = 0;
        for (byte b : bytes) {
            crc = ((crc << 8) ^ CRC16_TABLE[((crc >>> 8) ^ (b & 0xFF)) & 0xFF]) & 0xFFFF;
        }
        return crc;
    }

    /**
     * 【重点】Hash Tag 提取 —— 实现 Redis 的 {} 语义.
     *
     * 规则：取 key 中第一个 { 和第一个 } 之间的内容。
     * 如果 { 和 } 之间为空或不存在，返回整个 key。
     *
     * 示例：
     *   "order:{123}"      → "123"      → 强制 slot 相同
     *   "order:123"        → "order:123" → 散列到不同 slot
     *   "{user:100}.cache" → "user:100"  → 按用户分 slot
     */
    public String extractHashTag(String key) {
        int start = key.indexOf('{');
        if (start < 0) return key;
        int end = key.indexOf('}', start + 1);
        if (end < 0 || end == start + 1) return key; // {} 为空，无效 tag
        return key.substring(start + 1, end);
    }

    // ========================================================================
    // 槽位分布查询
    // ========================================================================

    /**
     * 查询某个 key 所属的节点.
     */
    public Map<String, Object> locateKey(String key) {
        int slot = slotForKey(key);
        String hashKey = extractHashTag(key);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("hashTag", hashKey.equals(key) ? "(无 Hash Tag)" : hashKey);
        result.put("slot", slot);
        result.put("crc16Hex", String.format("0x%04X", crc16(hashKey.getBytes())));

        for (Map.Entry<String, int[]> entry : nodeSlots.entrySet()) {
            int[] range = entry.getValue();
            if (slot >= range[0] && slot <= range[1]) {
                result.put("node", entry.getKey());
                result.put("slotRange", range[0] + "-" + range[1]);
                break;
            }
        }
        return result;
    }

    /**
     * 批量查询——演示多个 key 的槽位分布.
     */
    public Map<String, Object> locateKeys(List<String> keys) {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, List<String>> byNode = new LinkedHashMap<>();

        for (String key : keys) {
            Map<String, Object> loc = locateKey(key);
            results.add(loc);
            String node = (String) loc.getOrDefault("node", "unknown");
            byNode.computeIfAbsent(node, k -> new ArrayList<>()).add(key);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("keys", results);
        response.put("distribution", byNode);
        response.put("totalSlots", TOTAL_SLOTS);
        return response;
    }

    /**
     * 查看整个集群的槽位分配.
     */
    public Map<String, Object> clusterTopology() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSlots", TOTAL_SLOTS);
        result.put("totalNodes", nodeSlots.size());

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : nodeSlots.entrySet()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("node", entry.getKey());
            node.put("slotStart", entry.getValue()[0]);
            node.put("slotEnd", entry.getValue()[1]);
            node.put("slotCount", entry.getValue()[1] - entry.getValue()[0] + 1);
            nodes.add(node);
        }
        result.put("nodes", nodes);

        // 为什么是 16384？
        result.put("why16384", new String[]{
                "心跳消息中位图大小 = 16384 bits = 2048 bytes = 2KB，恰好适配以太网帧",
                "CRC16 输出 16 位空间，16384 = 2^14，取低 14 位",
                "扩展到 1000 节点时，每个节点平均 16.4 个槽，迁移粒度够细"
        });
        return result;
    }

    // ========================================================================
    // MOVED / ASK 重定向模拟
    // ========================================================================

    /**
     * 【重点】MOVED 重定向 —— 永久重定向.
     *
     * 场景：槽已永久迁移到目标节点。客户端收到 MOVED 后必须更新槽位映射表。
     *
     * Redis 协议格式：MOVED slot host:port
     *
     * 示例响应：
     *   GET mykey
     *   -MOVED 3999 192.168.3.100:7001
     */
    public Map<String, Object> simulateMoved(String key) {
        int slot = slotForKey(key);

        // 找到负责此槽的节点
        String correctNode = null;
        for (Map.Entry<String, int[]> entry : nodeSlots.entrySet()) {
            if (slot >= entry.getValue()[0] && slot <= entry.getValue()[1]) {
                correctNode = entry.getKey();
                break;
            }
        }

        // 构造一个"发错节点"的场景
        String wrongNode = correctNode;
        for (String node : nodeSlots.keySet()) {
            if (!node.equals(correctNode)) {
                wrongNode = node;
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "MOVED 重定向模拟");
        result.put("command", "GET " + key);
        result.put("sentTo", "错误节点: " + wrongNode);
        result.put("response", "-MOVED " + slot + " " + correctNode.split(" ")[0]);
        result.put("slot", slot);
        result.put("redirectType", "MOVED（永久重定向）");
        result.put("clientAction", "更新本地槽位映射表，后续该 slot 请求直接发到新节点");
        result.put("keyDifference", "MOVED 要求客户端永久更新拓扑；"
                + "ASK 只对本次请求有效，不更新拓扑");
        return result;
    }

    /**
     * 【重点】ASK 重定向 —— 临时重定向.
     *
     * 场景：槽正在迁移中，部分 key 已到目标节点。
     * 客户端收到 ASK 后只重试这一次，不更新槽表，但必须带上 ASKING 命令。
     *
     * Redis 协议：
     *   GET mykey
     *   -ASK 3999 192.168.3.100:7001
     *
     * 客户端必须：
     *   1. 先发送 ASKING（告诉目标节点"我知道槽还在迁移"）
     *   2. 再发送原始命令
     */
    public Map<String, Object> simulateAsk(String key) {
        int slot = slotForKey(key);

        String sourceNode = "7000 (192.168.3.100:7000)";
        String targetNode = "7001 (192.168.3.100:7001)";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "ASK 重定向模拟");
        result.put("command", "GET " + key);
        result.put("scenario", "槽 " + slot + " 正在从 " + sourceNode + " 迁移到 " + targetNode);
        result.put("sentTo", sourceNode);
        result.put("sourceState", "CLUSTER SETSLOT " + slot + " MIGRATING " + targetNode);
        result.put("targetState", "CLUSTER SETSLOT " + slot + " IMPORTING " + sourceNode);
        result.put("response", "-ASK " + slot + " " + targetNode.split(" ")[0]);
        result.put("redirectType", "ASK（临时重定向）");
        result.put("clientAction", new String[]{
                "1. 不要更新槽位映射表！",
                "2. 先向目标节点发送 ASKING 命令",
                "3. 再发送原始 GET " + key + " 命令",
                "4. 后续同一 slot 的请求仍发往源节点——因为槽还在迁移中"
        });
        result.put("keyDifference", "ASK 是「施工绕行」路牌（临时），"
                + "MOVED 是「永久搬迁」公告（永久）");
        return result;
    }

    /**
     * MOVED vs ASK 核心对比表.
     */
    public Map<String, Object> movedVsAsk() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "MOVED vs ASK —— 两个重定向的本质区别");

        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(row("触发时机", "槽已永久迁移完成", "槽正在迁移中"));
        rows.add(row("协议格式", "-MOVED slot host:port", "-ASK slot host:port"));
        rows.add(row("客户端行为", "更新本地 slot→node 映射", "不更新映射，仅重试本次"));
        rows.add(row("前置命令", "无需额外命令", "必须先发 ASKING"));
        rows.add(row("后续请求", "直接发到新节点", "仍发到原节点（迁移未完成）"));
        rows.add(row("类比", "永久搬迁公告 🏠→🏡", "施工绕行路牌 🚧"));
        result.put("comparison", rows);

        return result;
    }

    // ========================================================================
    // Hash Tag 演示
    // ========================================================================

    /**
     * 【重点】演示 Hash Tag 的作用.
     *
     * 输入一组 key，分别展示有 Hash Tag 和无 Hash Tag 时的槽位分布。
     * 这会非常直观地展示 Q4 中 Hash Tag 为什么能"强制同槽"。
     */
    public Map<String, Object> demonstrateHashTag(String tag) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hashTag", "{" + tag + "}");

        List<String> keysWithoutTag = Arrays.asList(
                "inventory:" + tag,
                "order:status:" + tag,
                "user:purchased:" + tag
        );
        List<String> keysWithTag = Arrays.asList(
                "inventory:{" + tag + "}",
                "order:status:{" + tag + "}",
                "user:purchased:{" + tag + "}"
        );

        // 无 Hash Tag
        List<Map<String, Object>> without = new ArrayList<>();
        for (String key : keysWithoutTag) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("slot", slotForKey(key));
            m.put("hashOf", extractHashTag(key));
            without.add(m);
        }
        result.put("withoutHashTag", without);

        // 有 Hash Tag
        List<Map<String, Object>> with = new ArrayList<>();
        for (String key : keysWithTag) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("slot", slotForKey(key));
            m.put("hashOf", extractHashTag(key));
            with.add(m);
        }
        result.put("withHashTag", with);

        // 结论
        Set<Integer> slotsWithout = new HashSet<>();
        Set<Integer> slotsWith = new HashSet<>();
        for (String k : keysWithoutTag) slotsWithout.add(slotForKey(k));
        for (String k : keysWithTag) slotsWith.add(slotForKey(k));

        result.put("conclusion", "无 Hash Tag → 分散到 " + slotsWithout.size()
                + " 个不同 slot；有 Hash Tag → 全部集中在同一个 slot " + slotsWith);
        result.put("risk", "Hash Tag 解决了跨槽原子性问题，但引入了数据倾斜风险——"
                + "所有带同一 tag 的 key 都落在同一节点，该节点可能成为热点");

        return result;
    }

    /** 查看 Hash Tag 提取规则的内部行为. */
    public Map<String, Object> hashTagExamples() {
        List<Map<String, String>> examples = new ArrayList<>();

        String[][] cases = {
                {"order:{123}", "123", "Hash Tag 生效"},
                {"order:123", "order:123", "无 Hash Tag，整 key 哈希"},
                {"{user:100}.cache", "user:100", "Tag 在开头"},
                {"cache.{user:100}", "user:100", "Tag 在中间"},
                {"user:{}", "user:{}", "Tag 为空 → 无效，整 key 哈希"},
                {"user:{100}:{200}", "100", "多个 {} → 只取第一个"},
        };

        for (String[] c : cases) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("key", c[0]);
            row.put("extracted", c[1]);
            row.put("note", c[2]);
            examples.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examples", examples);
        result.put("rule", "取第一个 { 到第一个 } 之间的内容；"
                + "若 {} 之间为空或不存在 {}，则整 key 参与 CRC16");
        return result;
    }

    // ---- helpers -----------------------------------------------------------

    private Map<String, String> row(String aspect, String moved, String ask) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("维度", aspect);
        r.put("MOVED", moved);
        r.put("ASK", ask);
        return r;
    }
}
