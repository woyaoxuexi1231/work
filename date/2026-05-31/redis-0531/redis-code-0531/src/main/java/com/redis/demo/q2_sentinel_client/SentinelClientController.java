package com.redis.demo.q2_sentinel_client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Q2: Jedis vs Lettuce —— 连真实 Sentinel 对比.
 *
 * <pre>
 * 启动: mvn spring-boot:run -Dspring-boot.run.profiles=sentinel
 *
 * # 查看两种客户端的连接状态
 * curl http://localhost:8080/api/q2/jedis-status
 * curl http://localhost:8080/api/q2/lettuce-status
 *
 * # 故障转移实时观测（开两个终端）
 * 终端A: curl -N http://localhost:8080/api/q2/watch-failover
 * 终端B: docker stop redis-master
 *
 * # 断路器
 * curl http://localhost:8080/api/q2/circuit-breaker
 * curl -X POST http://localhost:8080/api/q2/circuit-breaker/open
 * </pre>
 */
@RestController
@RequestMapping("/api/q2")
public class SentinelClientController {

    private static final Logger log = LoggerFactory.getLogger(SentinelClientController.class);

    @Value("${spring.redis.sentinel.nodes:192.168.3.100:26379,192.168.3.100:26380,192.168.3.100:26381}")
    private String sentinelNodes;

    @Value("${spring.redis.sentinel.master:mymaster}")
    private String masterName;

    @Value("${spring.redis.password:123456}")
    private String password;

    private JedisSentinelPool jedisPool;
    private LettuceConnectionFactory lettuceFactory;
    private StringRedisTemplate lettuceTemplate;

    // ---- 断路器（手写，不引入 Resilience4j） ----
    private volatile boolean circuitOpen;
    private volatile long circuitOpenTime;
    private final AtomicInteger failures = new AtomicInteger(0);
    private static final int THRESHOLD = 5;
    private static final long TIMEOUT_MS = 10_000;

    // ========================================================================
    // JedisSentinelPool
    // ========================================================================

    /**
     * JedisSentinelPool 连接状态.
     *
     * 面试点: "JedisSentinelPool 怎么知道当前主是谁？"
     * 答: 启动时连任意 Sentinel 执行 SENTINEL get-master-addr-by-name，
     *     同时订阅 +switch-master 频道，切换时 Sentinel 推送新主地址 → 重建池。
     */
    @GetMapping("/jedis-status")
    public Map<String, Object> jedisStatus() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("client", "JedisSentinelPool");

        if (jedisPool == null) {
            initJedis();
        }

        r.put("discovery", "SENTINEL get-master-addr-by-name " + masterName
                + " + 订阅 +switch-master");
        r.put("poolConfig", "maxTotal=8, maxIdle=4, testOnBorrow=true");

        // 当前连接指向谁
        try (Jedis j = jedisPool.getResource()) {
            r.put("connectedTo", j.getClient().getHost() + ":" + j.getClient().getPort());
            List<Object> role = j.role();
            r.put("role", role.isEmpty() ? "unknown" : String.valueOf(role.get(0)));
            r.put("ping", j.ping());
            r.put("activeCount", jedisPool.getNumActive());
            r.put("idleCount", jedisPool.getNumIdle());
        } catch (Exception e) {
            r.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            r.put("interviewNote", "这就是线上主从切换时最常见的异常——"
                    + "池里还缓存着指向旧主的连接，借出来一用就报错");
        }

        r.put("switchingWindow",
                "有短暂不可用窗口：旧连接未清空 + 新池未建完。"
                        + "对比 Lettuce 无缝切换（见 /api/q2/lettuce-status）");
        return r;
    }

    private void initJedis() {
        Set<String> sentinels = new HashSet<>();
        for (String s : sentinelNodes.split(",")) sentinels.add(s.trim());

        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(8);
        cfg.setMaxIdle(4);
        cfg.setMinIdle(2);
        cfg.setMaxWaitMillis(3000);
        cfg.setTestOnBorrow(true);  //【重点】借连接时校验，减少 NoSuchElementException

        this.jedisPool = new JedisSentinelPool(masterName, sentinels, cfg, 3000, password);
        log.info("JedisSentinelPool 初始化完成");
    }

    // ========================================================================
    // Lettuce (Spring Boot 默认)
    // ========================================================================

    /**
     * Lettuce 连接状态.
     *
     * 面试点: "为什么 Spring Boot 2.x 默认用 Lettuce？"
     * 答: Netty NIO 多路复用，单连接即够；TopologyRefreshScheduler 自适应刷新；
     *     线程安全，无需池化。Jedis 的 BIO 模型天然不支持 WebFlux。
     */
    @GetMapping("/lettuce-status")
    public Map<String, Object> lettuceStatus() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("client", "Lettuce (Spring Boot 默认)");

        if (lettuceFactory == null) {
            initLettuce();
        }

        r.put("ioModel", "Netty NIO 多路复用——单连接服务多线程");
        r.put("topologyRefresh", "TopologyRefreshScheduler 自适应刷新——"
                + "收到 +switch-master 即时切换路由，无需重建池");

        try {
            r.put("ping", lettuceFactory.getConnection().ping());
            r.put("threadSafe", "StatefulRedisConnection 线程安全，天然并发");
            r.put("switchingWindow", "近乎无缝——没有池，没有清空→重建过程");
        } catch (Exception e) {
            r.put("error", e.getMessage());
        }
        return r;
    }

    private void initLettuce() {
        RedisSentinelConfiguration cfg = new RedisSentinelConfiguration();
        cfg.setMaster(masterName);
        for (String node : sentinelNodes.split(",")) {
            String[] p = node.trim().split(":");
            cfg.sentinel(p[0], Integer.parseInt(p[1]));
        }
        cfg.setPassword(password);

        this.lettuceFactory = new LettuceConnectionFactory(cfg);
        this.lettuceFactory.afterPropertiesSet();
        this.lettuceTemplate = new StringRedisTemplate(lettuceFactory);
        log.info("Lettuce Sentinel 连接初始化完成");
    }

    // ========================================================================
    // 故障转移实时观测
    // ========================================================================

    /**
     * 每秒轮询 Sentinel: "现在谁是主？"
     *
     * 终端A: curl -N http://localhost:8080/api/q2/watch-failover
     * 终端B: docker stop redis-master
     *
     * 你将看到 master 从 6379 → NONE（切换中）→ 6380 ★★★
     */
    @GetMapping(value = "/watch-failover", produces = "text/plain")
    public String watchFailover() throws InterruptedException {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q2: 故障转移实时观测 ===\n");
        sb.append("另开终端执行: docker stop redis-master\n");
        sb.append("观察 master 如何从 6379 → NONE → 6380\n\n");

        String prev = "";
        for (int i = 0; i < 60; i++) {
            String master = queryMasterFromSentinel();
            String changed = (!prev.isEmpty() && !master.equals(prev))
                    ? " ★★★ 故障转移完成！新主上线" : "";
            sb.append(String.format("[%2ds] %s%s\n", i, master, changed));
            prev = master;
            Thread.sleep(1000);
        }
        return sb.toString();
    }

    /** 直接问 Sentinel 当前主是谁（绕过客户端缓存）. */
    private String queryMasterFromSentinel() {
        for (String node : sentinelNodes.split(",")) {
            String[] parts = node.trim().split(":");
            try (Jedis j = new Jedis(parts[0], Integer.parseInt(parts[1]), 2000)) {
                j.auth(password);
                List<String> addr = j.sentinelGetMasterAddrByName(masterName);
                if (addr != null && addr.size() == 2)
                    return addr.get(0) + ":" + addr.get(1);
            } catch (Exception ignored) {}
        }
        return "NONE（切换中...）";
    }

    // ========================================================================
    // 断路器
    // ========================================================================

    /**
     * 手写断路器状态查询.
     *
     * 状态机: CLOSED → (连续失败5次) → OPEN(10s) → HALF_OPEN → CLOSED
     */
    @GetMapping("/circuit-breaker")
    public Map<String, Object> circuitBreaker() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("failures", failures.get());
        r.put("threshold", THRESHOLD);

        if (circuitOpen) {
            long remain = TIMEOUT_MS - (System.currentTimeMillis() - circuitOpenTime);
            if (remain > 0) {
                r.put("state", "OPEN");
                r.put("remainingMs", remain);
                r.put("note", "所有 Redis 请求直接拒绝，走降级逻辑");
            } else {
                circuitOpen = false;
                r.put("state", "HALF_OPEN");
                r.put("note", "试探性放行下一个请求——成功则 CLOSED，失败则重新 OPEN");
            }
        } else {
            r.put("state", "CLOSED");
        }
        return r;
    }

    @PostMapping("/circuit-breaker/open")
    public Map<String, Object> forceOpen() {
        circuitOpen = true;
        circuitOpenTime = System.currentTimeMillis();
        failures.set(THRESHOLD);
        log.warn("断路器 OPEN——模拟 Redis 不可达");
        return circuitBreaker();
    }

    @PostMapping("/circuit-breaker/reset")
    public Map<String, Object> reset() {
        circuitOpen = false;
        failures.set(0);
        return circuitBreaker();
    }

    // ========================================================================
    // 面试追问速答
    // ========================================================================

    /**
     * READONLY 错误成因与缓解.
     *
     * 场景: 主宕机 → Sentinel 切新主 → 旧主复活变 Slave →
     *       持有旧连接的客户端写旧主 → READONLY
     *
     * 缓解: 捕获 READONLY → 强制刷新拓扑 + 重试
     */
    @GetMapping("/readonly-explanation")
    public Map<String, Object> readonlyExplanation() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cause", "旧主复活后变成 Slave，但持有旧连接的客户端（如 JedisSentinelPool "
                + "还没收到 +switch-master）仍向旧主发写请求 → Redis 返回 READONLY");
        r.put("mitigation", new String[]{
                "1. 捕获 READONLY 异常（RedisSystemException）",
                "2. 强制触发拓扑刷新: jedisPool.destroy() → 重建",
                "3. Lettuce: 自动处理——TopologyRefreshScheduler 收到 +switch-master 即时切换",
                "4. 接入断路器——连续 READONLY 不应无限重试"
        });
        return r;
    }

    /**
     * 读写分离 + Sentinel 的额外复杂度.
     *
     * 追问: "如果要实现从库读的读写分离，结合 Sentinel 会有哪些额外复杂度？"
     */
    @GetMapping("/read-write-splitting")
    public Map<String, Object> readWriteSplitting() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("complexities", new LinkedHashMap<String, String>() {{
            put("从库变化感知", "需订阅 +slave / -slave 事件，动态更新从库列表，"
                    + "不能用静态配置的从库地址");
            put("从库延迟容忍", "主从复制有延迟，读从库可能拿到旧数据。"
                    + "对一致性要求高的场景（如订单状态）必须读主库");
            put("故障转移时从库重定向",
                    "主从切换后，所有 Slave 改追随新主——"
                            + "旧主变 Slave、新主 Slave 的拓扑全变了，读写路由表需全量更新");
            put("Lettuce 支持", "Lettuce 的 ReadFrom 策略可配置 MASTER_PREFERRED / "
                    + "REPLICA_PREFERRED / NEAREST，但需要配合拓扑刷新保持从库列表准确");
        }});
        return r;
    }

    @PreDestroy
    public void destroy() {
        if (jedisPool != null) jedisPool.close();
        if (lettuceFactory != null) lettuceFactory.destroy();
    }
}
