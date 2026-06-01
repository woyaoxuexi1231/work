package com.redis.demo.q1_sentinel_failover;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Q1: Sentinel 故障转移 —— 多线程真模拟.
 *
 * <h3>架构</h3>
 * <pre>
 *   每个 Sentinel 是一个独立线程，各自 PING master。
 *   共享状态通过 ConcurrentHashMap + AtomicReference 协调。
 *
 *   Sentinel-1 ──PING──┐
 *   Sentinel-2 ──PING──┼── Master（可被 kill）
 *   Sentinel-3 ──PING──┘
 *        │                    │
 *        └── 互相询问 SDOWN ──┘
 *        │
 *   Leader 选举（epoch + 投票）
 *        │
 *   选出最优 Slave → SLAVEOF NO ONE → 新 Master
 *        │
 *   旧 Master 复活 → SLAVEOF 新 Master → 变为 Slave
 * </pre>
 *
 * <h3>时间线</h3>
 * 所有事件实时推入 {@code timeline} 队列，通过 HTTP 接口一次性返回。
 */
public class FailoverSimulator {

    // ====== 配置 ======
    private int sentinelCount;
    private int quorum;
    private int downAfterMs;
    private int slaveCount;
    private int failoverTimeoutMs;

    // ====== 角色（共享状态） ======
    private volatile MasterNode master;
    private final List<SlaveNode> slaves = Collections.synchronizedList(new ArrayList<>());
    private final List<SentinelNode> sentinels = Collections.synchronizedList(new ArrayList<>());

    // ====== 脑裂用 ======
    private volatile boolean splitBrainMode;
    private volatile boolean minSlavesProtection;

    // ====== 时间线（线程安全队列） ======
    private final ConcurrentLinkedQueue<TimelineEvent> timeline = new ConcurrentLinkedQueue<>();
    private final AtomicLong clock = new AtomicLong(0);

    // ====== 同步控制 ======
    private CountDownLatch sdownLatch;      // 等待至少 quorum 个 Sentinel 检测到 SDOWN
    private CountDownLatch odownLatch;       // 等待 ODOWN 达成
    private CountDownLatch electionLatch;    // 等待 Leader 选出
    private CountDownLatch failoverLatch;    // 等待故障转移完成

    // ====== Leader 选举 ======
    private final AtomicLong epoch = new AtomicLong(0);
    private final AtomicReference<SentinelNode> leader = new AtomicReference<>();
    private final ConcurrentHashMap<String, Boolean> votes = new ConcurrentHashMap<>();

    // ====== 脑裂隔离 ======
    private volatile boolean networkPartition;  // true = 旧主被隔离

    // ---- configure ----------------------------------------------------------

    public void configure(int sentinelCount, int quorum, int downAfterMs,
                          int slaveCount, int failoverTimeoutMs) {
        this.sentinelCount = sentinelCount;
        this.quorum = quorum;
        this.downAfterMs = downAfterMs;
        this.slaveCount = slaveCount;
        this.failoverTimeoutMs = failoverTimeoutMs;
    }

    // ---- execute（主入口） ---------------------------------------------------

    /**
     * 启动多线程故障转移模拟.
     *
     * 调用后阻塞约 5-15 秒（取决于 downAfterMs），返回完整时间线。
     */
    public List<TimelineEvent> executeFailover() throws InterruptedException {
        timeline.clear();
        clock.set(0);
        leader.set(null);
        votes.clear();
        splitBrainMode = false;
        networkPartition = false;

        // ---- 创建角色 ----
        master = new MasterNode("master", "192.168.3.100", 6379);
        slaves.clear();
        for (int i = 0; i < slaveCount; i++) {
            SlaveNode s = new SlaveNode("slave-" + (i + 1),
                    "192.168.3.100", 6380 + i,
                    i == 0 ? 10 : 50,           // 第一个优先级最高
                    1000L + ThreadLocalRandom.current().nextLong(500));
            slaves.add(s);
        }

        sentinels.clear();
        sdownLatch   = new CountDownLatch(quorum);
        odownLatch    = new CountDownLatch(1);
        electionLatch = new CountDownLatch(1);
        failoverLatch = new CountDownLatch(1);

        for (int i = 0; i < sentinelCount; i++) {
            SentinelNode s = new SentinelNode("sentinel-" + (i + 1),
                    "192.168.3.100", 26379 + i);
            sentinels.add(s);
        }

        // ---- 启动所有 Sentinel 线程 ----
        event("PHASE_1", "【启动】" + sentinelCount + " 个 Sentinel 线程启动，"
                + "各自独立 PING master " + master);

        for (SentinelNode s : sentinels) {
            s.start();
        }

        // ---- 等待 2 秒（正常 PING 期），然后 kill master ----
        Thread.sleep(1500);

        event("MASTER_KILL", "⚠ 主节点 " + master + " 被 KILL（模拟宕机）");
        master.alive.set(false);

        // ---- 等待 SDOWN（至少 quorum 个 Sentinel 检测到） ----
        event("WAIT_SDOWN", "等待至少 " + quorum + " 个 Sentinel 检测到 SDOWN"
                + "（down-after=" + downAfterMs + "ms）...");
        boolean sdownOk = sdownLatch.await(downAfterMs + 5000, TimeUnit.MILLISECONDS);

        if (!sdownOk) {
            event("SDOWN_TIMEOUT", "✗ SDOWN 超时——不足 quorum 个 Sentinel 检测到故障");
            stopAllSentinels();
            return drainTimeline();
        }

        // ---- 等待 ODOWN ----
        event("WAIT_ODOWN", "等待 ODOWN 达成（各 Sentinel 互相确认）...");
        odownLatch.await(10, TimeUnit.SECONDS);

        // ---- 等待 Leader 选举 ----
        event("WAIT_LEADER", "等待 Leader Sentinel 选举...");
        electionLatch.await(10, TimeUnit.SECONDS);

        // ---- 等待故障转移完成 ----
        event("WAIT_FAILOVER", "等待 Leader 执行故障转移...");
        failoverLatch.await(10, TimeUnit.SECONDS);

        // ---- 旧主复活 → 变为 Slave ----
        Thread.sleep(1000);
        event("OLD_MASTER_REVIVE", "【旧主复活】" + master + " 重新上线");

        SentinelNode theLeader = leader.get();
        SlaveNode newMaster = theLeader != null ? theLeader.promotedSlave : null;

        if (newMaster != null) {
            master.becomeSlaveOf(newMaster);
            event("OLD_MASTER_SLAVEOF",
                    master.name + " 执行 SLAVEOF " + newMaster.host + ":" + newMaster.port
                            + " → 降级为 Slave，从新主同步数据");
            event("COMPLETE",
                    "══════ 故障转移完成 ══════\n"
                            + "  旧主: " + master.name + " → Slave（从 " + newMaster.name + " 同步）\n"
                            + "  新主: " + newMaster.name + " (" + newMaster.host + ":" + newMaster.port + ")\n"
                            + "  Leader: " + (theLeader != null ? theLeader.name : "none"));
        }

        stopAllSentinels();
        return drainTimeline();
    }

    // ---- 脑裂模拟 ------------------------------------------------------------

    public List<TimelineEvent> simulateSplitBrain(boolean withProtection)
            throws InterruptedException {
        timeline.clear();
        clock.set(0);
        leader.set(null);
        votes.clear();
        splitBrainMode = true;
        minSlavesProtection = withProtection;
        networkPartition = true; // 网络分区——旧主被隔离

        // 创建角色
        master = new MasterNode("old-master", "192.168.3.100", 6379);
        slaves.clear();
        for (int i = 0; i < 3; i++) {
            slaves.add(new SlaveNode("slave-" + (i + 1),
                    "192.168.3.100", 6380 + i, i == 0 ? 10 : 50,
                    1000L + ThreadLocalRandom.current().nextLong(500)));
        }
        sentinels.clear();
        sdownLatch   = new CountDownLatch(quorum > 0 ? quorum : 2);
        odownLatch    = new CountDownLatch(1);
        electionLatch = new CountDownLatch(1);
        failoverLatch = new CountDownLatch(1);

        for (int i = 0; i < (sentinelCount > 0 ? sentinelCount : 3); i++) {
            sentinels.add(new SentinelNode("sentinel-" + (i + 1),
                    "192.168.3.100", 26379 + i));
        }

        event("SPLIT_BRAIN", "══════ 脑裂场景开始 ══════");
        event("PARTITION",
                "机架 A（旧主 " + master + " + 旧客户端）⟷ 网络隔离 ⟷ 机架 B（Sentinel 群 + Slave 群）");
        event("PARTITION_DETAIL",
                "旧主看不到 Sentinel 和 Slave；Sentinel 看不到旧主但能看到 Slave");

        // 旧主在隔离中继续接收写入（单独线程模拟）
        Thread oldMasterWriter = new Thread(() -> {
            while (networkPartition && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(500);
                    if (minSlavesProtection) {
                        // 检查 min-slaves —— 联系不到任何 Slave → 拒绝写入
                        event("OLD_MASTER_PROTECT",
                                master.name + " 检查: 可连接 Slave = 0"
                                        + " < min-slaves-to-write(1) → 拒绝写入！");
                    } else {
                        event("OLD_MASTER_WRITE",
                                "旧客户端 → " + master.name + " 写入订单 #"
                                        + System.nanoTime() % 100000
                                        + " → 成功！（旧主不知已被「废黜」）");
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "old-master-writer");
        oldMasterWriter.setDaemon(true);

        // 启动所有线程
        for (SentinelNode s : sentinels) s.start();
        oldMasterWriter.start();

        // 等待 Sentinel 侧完成故障转移
        sdownLatch.await(downAfterMs + 5000, TimeUnit.MILLISECONDS);
        odownLatch.await(10, TimeUnit.SECONDS);
        electionLatch.await(10, TimeUnit.SECONDS);
        failoverLatch.await(10, TimeUnit.SECONDS);

        // 让旧主写线程再跑一会
        Thread.sleep(2000);
        networkPartition = false;
        oldMasterWriter.interrupt();

        if (withProtection) {
            event("AVOIDED", "✓ 脑裂被阻止！旧主自觉闭嘴（min-slaves-to-write），数据一致性保全");
        } else {
            event("DISASTER", "✗ 脑裂发生——两个 Master 同时写入，数据冲突！");
        }

        stopAllSentinels();
        return drainTimeline();
    }

    // ---- getters -----------------------------------------------------------

    public String getLeaderName() {
        SentinelNode l = leader.get();
        return l != null ? l.name : null;
    }

    public String getPromotedSlaveName() {
        SentinelNode l = leader.get();
        return l != null && l.promotedSlave != null
                ? l.promotedSlave.name : null;
    }

    public int getQuorum() { return quorum; }

    public List<TimelineEvent> getTimeline() {
        return drainTimeline();
    }

    // ---- internal -----------------------------------------------------------

    private void stopAllSentinels() {
        for (SentinelNode s : sentinels) s.running.set(false);
    }

    private void event(String phase, String description) {
        long elapsed = clock.addAndGet(
                System.currentTimeMillis() - (clock.get() == 0
                        ? System.currentTimeMillis() : clock.get()));
        // 使用实际时间
        timeline.add(new TimelineEvent(
                System.currentTimeMillis(),
                phase, description,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))));
    }

    private List<TimelineEvent> drainTimeline() {
        List<TimelineEvent> list = new ArrayList<>(timeline);
        list.sort(Comparator.comparingLong(e -> e.timestampMs));
        // 重算 elapsed（以第一个事件的时间戳为基准 0）
        if (!list.isEmpty()) {
            long base = list.get(0).timestampMs;
            for (TimelineEvent e : list) {
                e.elapsedMs = e.timestampMs - base;
            }
        }
        return list;
    }

    // ========================================================================
    // 数据类
    // ========================================================================

    /** Redis 主节点（共享可变状态）. */
    static class MasterNode {
        final String name, host;
        final int port;
        final AtomicBoolean alive = new AtomicBoolean(true);
        volatile String role = "master";       // master / slave
        volatile String masterHost, masterPort; // 当降为 slave 时指向新主

        MasterNode(String name, String host, int port) {
            this.name = name; this.host = host; this.port = port;
        }

        void becomeSlaveOf(SlaveNode newMaster) {
            this.role = "slave";
            this.masterHost = newMaster.host;
            this.masterPort = String.valueOf(newMaster.port);
        }

        @Override
        public String toString() {
            return name + "(" + host + ":" + port + ")";
        }
    }

    /** Redis 从节点. */
    static class SlaveNode {
        final String name, host;
        final int port;
        final int priority;
        final long replicationOffset;
        final AtomicBoolean alive = new AtomicBoolean(true);
        volatile boolean promoted; // 已被提升为主

        SlaveNode(String name, String host, int port, int priority, long offset) {
            this.name = name; this.host = host; this.port = port;
            this.priority = priority; this.replicationOffset = offset;
        }

        @Override
        public String toString() {
            return name + "(" + host + ":" + port
                    + ", priority=" + priority + ", offset=" + replicationOffset + ")";
        }
    }

    /** Sentinel 节点（Thread）——每个实例一个独立线程. */
    class SentinelNode extends Thread {
        final String name, host;
        final int port;
        final AtomicBoolean running = new AtomicBoolean(true);
        volatile boolean sdown;
        volatile boolean odown;
        volatile boolean votedThisEpoch;
        volatile SlaveNode promotedSlave; // Leader 选出并提升的新主

        SentinelNode(String name, String host, int port) {
            super(name);
            this.name = name; this.host = host; this.port = port;
        }

        @Override
        public void run() {
            event("SENTINEL_START",
                    "  [" + name + "] 线程启动，开始每 1 秒 PING " + master);

            //【重点】PING 循环：主存活 + 无网络分区 → PONG
            while (running.get() && master.alive.get() && !networkPartition) {
                try {
                    Thread.sleep(1000);
                    event("PING", "  [" + name + "] PING " + master + " → PONG ✓");
                } catch (InterruptedException e) { break; }
            }

            if (!running.get()) return;

            // Master 宕机 或 网络分区 → Sentinel 看不到 master
            String reason = !master.alive.get() ? "master 宕机" : "网络分区隔离";
            event("PING_FAIL",
                    "  [" + name + "] PING " + master + " → 无响应（" + reason + "）...");

            long sdownStart = System.currentTimeMillis();
            while (running.get()
                    && System.currentTimeMillis() - sdownStart < downAfterMs) {
                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
            }

            if (!running.get()) return;

            this.sdown = true;
            event("SDOWN",
                    "  [" + name + "] " + downAfterMs + "ms 内未收到 PONG"
                            + " → 标记 master 为 SDOWN（主观下线）");
            sdownLatch.countDown();

            // ================================================================
            // 询问其他 Sentinel → ODOWN
            // ================================================================
            event("SDOWN_ASK",
                    "  [" + name + "] 询问其他 Sentinel：你们看到 master 挂了吗？");

            // 等待其他 Sentinel 也检测到 SDOWN
            try { sdownLatch.await(downAfterMs + 3000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) { return; }

            int sdownCount = 0;
            for (SentinelNode s : sentinels) {
                if (s.sdown) sdownCount++;
            }

            event("ODOWN_CHECK",
                    "  [" + name + "] 确认 SDOWN 的 Sentinel 数: "
                            + sdownCount + "/" + sentinelCount);

            if (sdownCount >= quorum) {
                this.odown = true;
                event("ODOWN",
                        "  [" + name + "] 达到 quorum(" + quorum
                                + ") → 标记 master 为 ODOWN（客观下线）！");
                odownLatch.countDown();
            } else {
                event("ODOWN_FAIL",
                        "  [" + name + "] 未达 quorum(" + quorum
                                + ") → ODOWN 失败");
                return;
            }

            // ================================================================
            // Leader 选举（简化的 Raft —— 先到先得）
            // ================================================================
            long myEpoch = epoch.incrementAndGet();
            event("LEADER_ELECT",
                    "  [" + name + "] 发起 Leader 选举，epoch=" + myEpoch);

            // 收集投票
            int myVotes = 1; // 自己
            votes.put(name, true);

            for (SentinelNode s : sentinels) {
                if (s == this || !s.running.get()) continue;
                if (!s.votedThisEpoch) {
                    s.votedThisEpoch = true;
                    myVotes++;
                    event("LEADER_VOTE",
                            "  [" + s.name + "] → 投票给 " + name + "（YES）");
                }
            }

            int majority = sentinelCount / 2 + 1;
            if (myVotes >= majority) {
                leader.set(this);
                event("LEADER_ELECTED",
                        "✓ [" + name + "] 获得 " + myVotes + "/" + sentinelCount
                                + " 票 ≥ " + majority + " → 当选 Leader Sentinel！");
                electionLatch.countDown();
            } else {
                event("LEADER_FAIL",
                        "  [" + name + "] " + myVotes + " 票未过半数 → 等待其他 Sentinel");
                // 等待其他 Sentinel 当选
                try { electionLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) { return; }
            }

            // 只有 Leader 执行故障转移
            if (leader.get() != this) {
                event("LEADER_WAIT",
                        "  [" + name + "] 等待 Leader " + leader.get().name + " 完成故障转移...");
                return;
            }

            // ================================================================
            // 故障转移：选 Slave → 提升 → 通知
            // ================================================================
            event("SLAVE_SELECT",
                    "★★★★ [" + name + "] 扫描所有 Slave，选择最优候选 ★★★★");

            List<SlaveNode> candidates = new ArrayList<>(slaves);
            candidates.sort(Comparator
                    .comparingInt((SlaveNode s) -> s.priority)
                    .thenComparingLong(s -> s.replicationOffset)
                    .reversed());

            SlaveNode best = null;
            for (SlaveNode s : candidates) {
                event("SLAVE_SCAN",
                        "    检查 " + s.name + ": priority=" + s.priority
                                + ", offset=" + s.replicationOffset);
                if (best == null) best = s;
            }

            if (best == null) {
                event("NO_SLAVE", "✗ 无可用 Slave！故障转移失败");
                return;
            }

            // 提升 Slave 为新主
            this.promotedSlave = best;
            best.promoted = true;

            event("PROMOTE",
                    "★★★★ [" + name + "] 向 " + best.name + "("
                            + best.host + ":" + best.port
                            + ") 发送 SLAVEOF NO ONE → 提升为 MASTER ★★★★");

            // 通知其他 Slave 追随新主
            for (SlaveNode s : slaves) {
                if (s != best) {
                    event("REPLICA_RECONFIG",
                            "  [" + name + "] 通知 " + s.name
                                    + " → SLAVEOF " + best.host + " " + best.port);
                }
            }

            // Pub/Sub 广播
            event("SWITCH_MASTER",
                    "★★★★ [" + name + "] 通过 +switch-master 频道广播: "
                            + "新主 = " + best.host + ":" + best.port + " ★★★★");

            failoverLatch.countDown();
        }
    }

    /** 时间线事件（线程安全）. */
    public static class TimelineEvent {
        long elapsedMs;
        final String phase;
        final String description;
        final String timestamp;
        final long timestampMs;

        TimelineEvent(long timestampMs, String phase, String description, String timestamp) {
            this.timestampMs = timestampMs;
            this.phase = phase;
            this.description = description;
            this.timestamp = timestamp;
        }

        public long getElapsedMs()       { return elapsedMs; }
        public String getPhase()         { return phase; }
        public String getDescription()   { return description; }
        public String getTimestamp()     { return timestamp; }
        public long getTimestampMs()     { return timestampMs; }
    }

}
