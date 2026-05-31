package com.redis.demo.controller;

import com.redis.demo.sentinel.FailoverSimulator;
import com.redis.demo.sentinel.FailoverSimulator.TimelineEvent;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Q1: Sentinel 故障转移 & 脑裂 —— HTTP 接口.
 *
 * <h3>调用路径</h3>
 * <pre>
 *   # 1. 先配置参数
 *   curl -X POST http://localhost:8080/api/sentinel/failover/configure \
 *     -H 'Content-Type: application/json' \
 *     -d '{"sentinelCount":3, "quorum":2, "downAfterMs":3000, "slaveCount":3, "failoverTimeoutMs":30000}'
 *
 *   # 2. 执行故障转移
 *   curl -X POST http://localhost:8080/api/sentinel/failover/trigger
 *
 *   # 3. 查看时间线
 *   curl http://localhost:8080/api/sentinel/failover/timeline
 *
 *   # 4. 模拟脑裂（无防护）
 *   curl -X POST http://localhost:8080/api/sentinel/split-brain/simulate?protection=false
 *
 *   # 5. 模拟脑裂（有防护）
 *   curl -X POST http://localhost:8080/api/sentinel/split-brain/simulate?protection=true
 * </pre>
 */
@RestController
@RequestMapping("/api/sentinel")
public class SentinelFailoverController {

    private final FailoverSimulator simulator = new FailoverSimulator();

    /**
     * 【重点】配置故障转移参数.
     *
     * 关键参数：
     * - sentinelCount: Sentinel 节点数 —— 改为 2 观察 quorum 无法达成
     * - quorum: 法定人数 —— 越接近 sentinelCount 越严格
     * - downAfterMs: 主观下线检测窗口 —— 过短会误判，过长 RTO 变大
     * - slaveCount: 从节点数量 —— 影响"选择新主"阶段的候选池
     */
    @PostMapping("/failover/configure")
    public Map<String, Object> configure(@RequestBody Map<String, Integer> params) {
        int sentinelCount    = params.getOrDefault("sentinelCount", 3);
        int quorum           = params.getOrDefault("quorum", 2);
        int downAfterMs      = params.getOrDefault("downAfterMs", 3000);
        int slaveCount       = params.getOrDefault("slaveCount", 3);
        int failoverTimeoutMs = params.getOrDefault("failoverTimeoutMs", 30000);

        simulator.configure(sentinelCount, quorum, downAfterMs, slaveCount, failoverTimeoutMs);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "configured");
        resp.put("sentinelCount", sentinelCount);
        resp.put("quorum", quorum);
        resp.put("downAfterMs", downAfterMs + "ms");
        resp.put("slaveCount", slaveCount);
        resp.put("failoverTimeoutMs", failoverTimeoutMs + "ms");
        resp.put("tip", "已配置完成。POST /api/sentinel/failover/trigger 开始模拟");
        return resp;
    }

    /**
     * 【核心】触发完整故障转移流程.
     *
     * 返回 JSON 包含：
     * - timeline: 每个阶段的时间点、事件名、详细描述
     * - leader: 当选的 Leader Sentinel
     * - promotedSlave: 被提升为主的新节点
     * - totalEvents: 事件总数
     *
     * 观察重点：搜索 "SDOWN → ODOWN → LEADER → PROMOTED" 这四个 phase 的跃迁
     */
    @PostMapping("/failover/trigger")
    public Map<String, Object> trigger() {
        List<TimelineEvent> timeline = simulator.executeFailover();

        Map<String, Object> resp = new HashMap<>();
        resp.put("title", "Sentinel 故障转移全流程");
        resp.put("totalEvents", timeline.size());
        resp.put("timeline", timeline);

        if (simulator.getLeader() != null) {
            resp.put("leader", simulator.getLeader().getName());
        }
        if (simulator.getMaster().getPromotedSlave() != null) {
            resp.put("promotedSlave",
                    simulator.getMaster().getPromotedSlave().getName());
        }
        resp.put("watchFor", "观察 phase 字段的递进: "
                + "SDOWN → ODOWN → LEADER_ELECTION_START → LEADER_ELECTED → PROMOTED");
        return resp;
    }

    /**
     * 查看最近一次故障转移的完整时间线.
     */
    @GetMapping("/failover/timeline")
    public Map<String, Object> timeline() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("totalEvents", simulator.getTimeline().size());
        resp.put("timeline", simulator.getTimeline());
        return resp;
    }

    /**
     * 【重点】脑裂（Split-Brain）模拟.
     *
     * @param protection true = 开启 min-slaves-to-write 防护;
     *                   false = 无防护，两个 Master 同时写入
     *
     * 调用对比:
     *   curl -X POST 'http://localhost:8080/api/sentinel/split-brain/simulate?protection=false'
     *   curl -X POST 'http://localhost:8080/api/sentinel/split-brain/simulate?protection=true'
     *
     * 观察返回值中是否出现 "PROTECTION_ON" / "AVOIDED" vs "CONFLICT" / "DISASTER"
     */
    @PostMapping("/split-brain/simulate")
    public Map<String, Object> simulateSplitBrain(
            @RequestParam(defaultValue = "false") boolean protection) {

        List<TimelineEvent> timeline = simulator.simulateSplitBrain(protection);

        Map<String, Object> resp = new HashMap<>();
        resp.put("title", "脑裂模拟" + (protection ? "（有 min-slaves-to-write 防护）"
                : "（无防护 - 危险！）"));
        resp.put("protectionEnabled", protection);
        resp.put("totalEvents", timeline.size());
        resp.put("timeline", timeline);

        if (protection) {
            resp.put("result", "脑裂被阻止——旧主自觉拒绝写入");
            resp.put("principle", "min-slaves-to-write + min-slaves-max-lag "
                    + "让旧主在网络隔离时「自觉闭嘴」，这是数据一致性的最后防线");
        } else {
            resp.put("result", "脑裂发生——两个 Master 同时写入，数据冲突！");
            resp.put("lesson", "quorum 解决「能不能切」，"
                    + "min-slaves-to-write 解决「旧主该不该闭嘴」。二者缺一不可");
        }
        return resp;
    }

    /**
     * 当前模拟器状态概览.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("master", simulator.getMaster() != null
                ? simulator.getMaster().getName() : "none");
        resp.put("slaveCount", simulator.getSlaves().size());
        resp.put("sentinelCount", simulator.getSentinels().size());
        resp.put("quorum", simulator.getQuorum());
        resp.put("hasLeader", simulator.getLeader() != null);
        resp.put("endpoints", new String[]{
                "POST /api/sentinel/failover/configure  — 配置参数",
                "POST /api/sentinel/failover/trigger     — 触发故障转移",
                "GET  /api/sentinel/failover/timeline     — 查看时间线",
                "POST /api/sentinel/split-brain/simulate   — 脑裂模拟",
                "GET  /api/sentinel/status                  — 当前状态"
        });
        return resp;
    }
}
