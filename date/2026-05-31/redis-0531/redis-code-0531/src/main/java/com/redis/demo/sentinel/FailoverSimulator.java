package com.redis.demo.sentinel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Sentinel 故障转移全流程模拟器 —— 核心引擎.
 *
 * <h3>这段代码在做什么？</h3>
 * 它不是在连接真实的 Sentinel，而是用 Java 对象扮演一个微型 Sentinel 集群，
 * 把 SDOWN → ODOWN → Leader 选举 → 新主上位 每一步的时间点和状态变迁
 * 都记录成 {@link TimelineEvent}，让你通过 HTTP 接口亲眼看到整个过程。
 *
 * <h3>对应面试题 Q1</h3>
 * <ul>
 *   <li>主观下线 SDOWN：单个 Sentinel 在 down-after-milliseconds 内收不到 PONG，标记主为 SDOWN</li>
 *   <li>客观下线 ODOWN：quorum 个 Sentinel 都确认 SDOWN，达成共识</li>
 *   <li>Leader 选举：Raft 协议简化版——先到先得，一任内只投一次票</li>
 *   <li>故障转移：Leader 选择最优 Slave（优先级→偏移量→运行ID），SLAVEOF NO ONE 提升为主</li>
 * </ul>
 *
 * <h3>你可以调整的参数（加进请求体）</h3>
 * <ul>
 *   <li>{@code sentinelCount}: Sentinel 节点数（默认 3，改 2 看"无法达成 quorum"）</li>
 *   <li>{@code quorum}: 法定人数（默认 2）</li>
 *   <li>{@code downAfterMs}: 主观下线检测窗口（默认 3000ms）</li>
 *   <li>{@code slaveCount}: 从节点数量</li>
 * </ul>
 */
public class FailoverSimulator {

    // ====== 配置参数 ======
    private int sentinelCount;
    private int quorum;
    private int downAfterMs;
    private int slaveCount;
    private int failoverTimeoutMs;

    // ====== 角色扮演 ======
    private RedisNode master;
    private final List<RedisNode> slaves = new ArrayList<>();
    private final List<SentinelNode> sentinels = new ArrayList<>();
    private SentinelNode leader; //【重点】被选举出的 Leader Sentinel

    // ====== 时间线记录 ======
    private final List<TimelineEvent> timeline = new ArrayList<>();
    private long epoch = 0; //【重点】纪元——Raft 协议的核心，每次选举自增
    private long startTime;

    // ====== 脑裂专用 ======
    private boolean splitBrainActive = false;
    private boolean minSlavesToWriteEnabled = false;

    // ---- construct / reset --------------------------------------------------

    /**
     * 根据传入参数初始化模拟环境.
     */
    public void configure(int sentinelCount, int quorum, int downAfterMs,
                          int slaveCount, int failoverTimeoutMs) {
        this.sentinelCount = sentinelCount;
        this.quorum = quorum;
        this.downAfterMs = downAfterMs;
        this.slaveCount = slaveCount;
        this.failoverTimeoutMs = failoverTimeoutMs;
        this.timeline.clear();
        this.leader = null;
        this.splitBrainActive = false;

        // 初始化主节点
        this.master = new RedisNode("master", "192.168.3.100", 6379,
                RedisNode.Role.MASTER, System.currentTimeMillis());

        // 初始化从节点
        this.slaves.clear();
        for (int i = 0; i < slaveCount; i++) {
            long offset = 1000L + new Random().nextInt(500); // 模拟不同复制偏移量
            RedisNode slave = new RedisNode("slave-" + (i + 1),
                    "192.168.3.100", 6380 + i, RedisNode.Role.SLAVE, offset);
            // 第一个 slave 优先级最高
            slave.setPriority(i == 0 ? 10 : 50);
            this.slaves.add(slave);
        }

        // 初始化 Sentinel 节点
        this.sentinels.clear();
        for (int i = 0; i < sentinelCount; i++) {
            this.sentinels.add(new SentinelNode("sentinel-" + (i + 1),
                    "192.168.3.100", 26379 + i));
        }
    }

    // ---- 核心流程：故障转移 -------------------------------------------------

    /**
     * 【重点】完整故障转移流程——这是面试时要脱口而出的六个阶段.
     */
    public List<TimelineEvent> executeFailover() {
        timeline.clear();
        startTime = System.currentTimeMillis();

        // ================================================================
        // 阶段 1：正常状态 — 所有 Sentinel 定期 PING master
        // ================================================================
        addEvent("PHASE_1", "【正常】所有 Sentinel 每 1 秒 PING 主节点 "
                + master.getHost() + ":" + master.getPort());
        for (SentinelNode s : sentinels) {
            addEvent("PING_OK", s.getName() + " PING master → PONG ✓");
        }

        // ================================================================
        // 阶段 2：master 宕机 — PING 超时，SDOWN 判定
        // ================================================================
        sleepDownAfter(); //【重点】模拟 down-after-milliseconds 窗口
        master.setAlive(false);
        addEvent("MASTER_DOWN", "⚠ 主节点 " + master.getHost() + ":" + master.getPort()
                + " 宕机（模拟）");

        List<SentinelNode> sdownSentinels = new ArrayList<>();
        for (SentinelNode s : sentinels) {
            if (new Random().nextBoolean() || sentinels.indexOf(s) == 0) {
                // 第一个 Sentinel 一定检测到（保证流程能推进）
                s.setSdown(true);
                sdownSentinels.add(s);
                addEvent("SDOWN", s.getName() + " 在 " + downAfterMs + "ms 内未收到 PONG"
                        + " → 标记 master 为 SDOWN（主观下线）");
            } else {
                addEvent("SDOWN_PENDING", s.getName() + " 仍在等待 PONG 回复...");
            }
        }

        // ================================================================
        // 阶段 3：ODOWN — quorum 投票，客观下线
        // ================================================================
        int confirmCount = 0;
        for (SentinelNode s : sentinels) {
            // 通过"询问其他 Sentinel"来确认
            if (sdownSentinels.contains(s) || new Random().nextInt(10) < 8) {
                s.setOdown(true);
                confirmCount++;
                addEvent("ODOWN_VOTE", s.getName() + " 确认 master 不可达 → 投票：YES");
            } else {
                addEvent("ODOWN_VOTE", s.getName() + " 网络抖动中 → 投票：NO");
            }
        }

        //【重点】quorum 判定 —— 这是 ODOWN 与 SDOWN 的本质区别
        if (confirmCount >= quorum) {
            addEvent("ODOWN", "✓ 达到法定人数！" + confirmCount + "/" + sentinelCount
                    + " ≥ quorum(" + quorum + ") → 标记 master 为 ODOWN（客观下线）");
        } else {
            addEvent("ODOWN_FAILED", "✗ 未达 quorum！" + confirmCount + "/" + sentinelCount
                    + " < quorum(" + quorum + ") → 故障转移中止！");
            return timeline;
        }

        // ================================================================
        // 阶段 4：Leader 选举 — Raft 协议简化版
        // ================================================================
        epoch++;
        addEvent("LEADER_ELECTION_START", "【Leader 选举】纪元 epoch=" + epoch
                + " 开始，Sentinel 们竞争 Leader 角色");
        addEvent("RAFT_NOTE", "【原理】Raft 协议子集：先请求投票者胜出，"
                + "每个 Sentinel 一任内只投一次票");

        // 第一个检测到 SDOWN 的 Sentinel 发起选举（现实中也基本如此）
        SentinelNode candidate = sdownSentinels.get(0);
        candidate.setEpoch(epoch);

        int votes = 1; // 自己的一票
        addEvent("LEADER_VOTE", candidate.getName() + " 发起选举，纪元=" + epoch
                + " → 自己投 YES（1 票）");

        for (SentinelNode s : sentinels) {
            if (s != candidate) {
                if (!s.isVotedThisEpoch()) {
                    s.setVotedThisEpoch(true);
                    votes++;
                    addEvent("LEADER_VOTE", s.getName() + " 收到投票请求 → YES（"
                            + votes + " 票）");
                }
            }
        }

        //【重点】过半即当选 Leader
        int majority = sentinelCount / 2 + 1;
        if (votes >= majority) {
            leader = candidate;
            addEvent("LEADER_ELECTED", "✓ " + leader.getName() + " 获得 " + votes
                    + "/" + sentinelCount + " 票 ≥ 多数(" + majority + ")"
                    + " → 当选为 Leader Sentinel");
        } else {
            addEvent("LEADER_FAILED", "✗ " + votes + " 票未过半数(" + majority + ")"
                    + " → 等待下一轮选举");
            return timeline;
        }

        // ================================================================
        // 阶段 5：选择新主 — 优先级→偏移量→运行ID
        // ================================================================
        addEvent("SLAVE_SELECTION", "【选择新主】Leader 扫描所有健康 Slave");

        // 排序规则：优先级高→复制偏移量大→运行ID字典序
        slaves.sort(Comparator
                .comparingInt(RedisNode::getPriority)
                .thenComparingLong(RedisNode::getReplicationOffset)
                .reversed());

        RedisNode bestSlave = null;
        for (RedisNode slave : slaves) {
            if (slave.isAlive()) {
                addEvent("SLAVE_CHECK", "  " + slave.getName()
                        + " 优先级=" + slave.getPriority()
                        + " 偏移量=" + slave.getReplicationOffset()
                        + " → " + (bestSlave == null ? "候选" : "比较"));
                if (bestSlave == null) bestSlave = slave;
            }
        }

        if (bestSlave == null) {
            addEvent("NO_SLAVE", "✗ 没有可用的 Slave → 故障转移失败");
            return timeline;
        }

        // ================================================================
        // 阶段 6：执行故障转移 — SLAVEOF NO ONE
        // ================================================================
        addEvent("FAILOVER_EXEC", "【执行转移】Leader " + leader.getName()
                + " 向 " + bestSlave.getName() + " 发送 SLAVEOF NO ONE");

        // 旧主降级（如果还活着）
        master.setRole(RedisNode.Role.SLAVE);
        // 新主升级
        bestSlave.setRole(RedisNode.Role.MASTER);
        bestSlave.setPromotionTime(System.currentTimeMillis());
        master.setPromotedSlave(bestSlave);

        addEvent("PROMOTED", "✓ " + bestSlave.getName() + "("
                + bestSlave.getHost() + ":" + bestSlave.getPort()
                + ") 升级为新 MASTER！");

        // 通知其他 Slave 复制新主
        for (RedisNode slave : slaves) {
            if (slave != bestSlave && slave.isAlive()) {
                addEvent("REPLICA_RECONFIG", "Leader 通知 " + slave.getName()
                        + " → SLAVEOF " + bestSlave.getHost()
                        + " " + bestSlave.getPort());
            }
        }

        // Pub/Sub 广播
        addEvent("SWITCH_MASTER", "【广播】通过 +switch-master 频道通知所有客户端："
                + "新主 = " + bestSlave.getHost() + ":" + bestSlave.getPort());

        // 总耗时
        long totalMs = System.currentTimeMillis() - startTime;
        addEvent("COMPLETE", "故障转移完成！总耗时 ≈ " + totalMs + "ms (检测:"
                + downAfterMs + "ms + 选举+转移开销)");

        return timeline;
    }

    // ---- 脑裂模拟 ----------------------------------------------------------

    /**
     * 【重点】脑裂（Split-Brain）模拟.
     *
     * 场景：网络分区导致旧主与 Sentinel 集群隔离。
     * Sentinel 群选举出新主，但旧主未感知，仍接受客户端写入
     * → 两个 Master 同时对外服务 → 数据冲突。
     *
     * 解决方案：旧主配置 min-slaves-to-write / min-slaves-max-lag
     * → 发现自己联系不到足够 Slave 时，拒绝写入。
     */
    public List<TimelineEvent> simulateSplitBrain(boolean withProtection) {
        timeline.clear();
        splitBrainActive = true;
        minSlavesToWriteEnabled = withProtection;

        addEvent("SPLIT_BRAIN", "【脑裂模拟】网络分区：旧主与 Sentinel 集群隔离");

        // 先执行正常故障转移（Sentinel 侧选新主）
        List<TimelineEvent> failoverEvents = executeFailover();
        // 清除 failover 的时间线，重新组织为脑裂叙事
        timeline.clear();

        addEvent("SCENARIO", "══════ 脑裂场景开始 ══════");
        addEvent("PARTITION", "机架 A（旧主 " + master.getHost() + ":" + master.getPort()
                + "）⟷ 网络隔离 ⟷ 机架 B（Sentinel + Slave 群）");

        // Sentinel 侧：选举新主
        if (master.getPromotedSlave() != null) {
            addEvent("SENTINEL_SIDE", "Sentinel 侧：检测到旧主不可达 → 选举 "
                    + master.getPromotedSlave().getName() + " 为新主");
        }

        // 旧主侧：网络隔离中，旧主仍在接收写入
        addEvent("OLD_MASTER_SIDE", "旧主侧：网络隔离中，旧主不知道自己已被「废黜」");
        addEvent("OLD_MASTER_WRITE", "旧客户端 → 旧主写入订单 #" + System.currentTimeMillis()
                + " → 写入成功（旧主仍自认为是 Master）");

        //【重点】关键分歧点
        if (withProtection) {
            addEvent("PROTECTION_ON", "【防护开启】旧主配置: min-slaves-to-write=1, "
                    + "min-slaves-max-lag=10");
            addEvent("PROTECTION_CHECK", "旧主检查: 可连接的 Slave 数量 = 0"
                    + " < min-slaves-to-write(1) → 拒绝所有写入！");
            addEvent("AVOIDED", "✓ 脑裂被阻止！旧主自觉闭嘴，数据一致性得以保全");
        } else {
            addEvent("PROTECTION_OFF", "【无防护】旧主未配置 min-slaves-to-write");
            addEvent("CONFLICT", "✗ 两个 Master 同时写入！订单号可能重复，库存数据错乱！");
            addEvent("DISASTER", "【血泪教训】quorum 决定了「能不能切」，"
                    + "min-slaves-to-write 决定了「旧主该不该闭嘴」");
        }

        splitBrainActive = false;
        return timeline;
    }

    // ---- 辅助方法 ----------------------------------------------------------

    private void sleepDownAfter() {
        try {
            Thread.sleep(Math.min(downAfterMs, 2000)); // 演示中限制等待，不真等太久
        } catch (InterruptedException ignored) {
        }
    }

    private void addEvent(String phase, String description) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        timeline.add(new TimelineEvent(
                System.currentTimeMillis() - startTime,
                phase, description, timestamp));
    }

    // ---- getters -----------------------------------------------------------

    public List<TimelineEvent> getTimeline()     { return timeline; }
    public SentinelNode getLeader()              { return leader; }
    public RedisNode getMaster()                 { return master; }
    public List<RedisNode> getSlaves()           { return slaves; }
    public List<SentinelNode> getSentinels()     { return sentinels; }
    public int getQuorum()                       { return quorum; }

    // ========================================================================
    // 内部数据类
    // ========================================================================

    /** Sentinel 节点. */
    public static class SentinelNode {
        private final String name, host;
        private final int port;
        private long epoch;
        private boolean sdown, odown, votedThisEpoch;

        public SentinelNode(String name, String host, int port) {
            this.name = name; this.host = host; this.port = port;
        }
        public String getName()            { return name; }
        public String getHost()            { return host; }
        public int getPort()               { return port; }
        public long getEpoch()             { return epoch; }
        public boolean isSdown()           { return sdown; }
        public boolean isOdown()           { return odown; }
        public boolean isVotedThisEpoch()  { return votedThisEpoch; }
        public void setEpoch(long e)       { this.epoch = e; }
        public void setSdown(boolean s)    { this.sdown = s; }
        public void setOdown(boolean o)    { this.odown = o; }
        public void setVotedThisEpoch(boolean v) { this.votedThisEpoch = v; }
    }

    /** Redis 节点（主/从）. */
    public static class RedisNode {
        public enum Role { MASTER, SLAVE }

        private final String name, host;
        private final int port;
        private Role role;
        private boolean alive = true;
        private long replicationOffset;
        private int priority = 100;
        private long promotionTime;
        private RedisNode promotedSlave; // 引用新主（脑裂场景用）

        public RedisNode(String name, String host, int port, Role role, long offset) {
            this.name = name; this.host = host; this.port = port;
            this.role = role; this.replicationOffset = offset;
        }
        public String getName()              { return name; }
        public String getHost()              { return host; }
        public int getPort()                 { return port; }
        public Role getRole()                { return role; }
        public boolean isAlive()             { return alive; }
        public long getReplicationOffset()   { return replicationOffset; }
        public int getPriority()             { return priority; }
        public long getPromotionTime()       { return promotionTime; }
        public RedisNode getPromotedSlave()  { return promotedSlave; }
        public void setRole(Role r)          { this.role = r; }
        public void setAlive(boolean a)      { this.alive = a; }
        public void setPriority(int p)       { this.priority = p; }
        public void setPromotionTime(long t) { this.promotionTime = t; }
        public void setPromotedSlave(RedisNode s) { this.promotedSlave = s; }
    }

    /** 时间线事件. */
    public static class TimelineEvent {
        private final long elapsedMs;
        private final String phase, description, timestamp;

        public TimelineEvent(long elapsedMs, String phase, String description, String timestamp) {
            this.elapsedMs = elapsedMs; this.phase = phase;
            this.description = description; this.timestamp = timestamp;
        }
        public long getElapsedMs()       { return elapsedMs; }
        public String getPhase()         { return phase; }
        public String getDescription()   { return description; }
        public String getTimestamp()     { return timestamp; }
    }
}
