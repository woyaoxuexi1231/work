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
 * Q2: Jedis vs Lettuce —— 用真实 Sentinel 演示三种坑点.
 *
 * <pre>
 * 启动: mvn spring-boot:run -Dspring-boot.run.profiles=sentinel
 *
 * # 1. 看两种客户端当前状态
 * curl http://localhost:8080/api/q2/status
 *
 * # 2. 故障转移压力测试（先开这个，再 docker stop redis-master）
 * curl -X POST http://localhost:8080/api/q2/failover-test?times=20
 *
 * # 3. 断路器演示
 * curl http://localhost:8080/api/q2/circuit-breaker
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

    private volatile JedisSentinelPool jedisPool;
    private volatile LettuceConnectionFactory lettuceFactory;

    // ---- 断路器 ----
    private final AtomicInteger failures = new AtomicInteger(0);
    private static final int THRESHOLD = 5;

    // ================================================================
    // 端点 1: 当前状态——两种客户端各自看到了什么
    // ================================================================

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> r = new LinkedHashMap<>();

        // ---- Jedis ----
        Map<String, Object> jedis = new LinkedHashMap<>();
        jedis.put("client", "JedisSentinelPool");
        jedis.put("discovery", "启动时 SENTINEL get-master-addr-by-name + 订阅 +switch-master");
        if (jedisPool == null) initJedis();
        try (Jedis j = jedisPool.getResource()) {
            jedis.put("master", j.getClient().getHost() + ":" + j.getClient().getPort());
            jedis.put("role", String.valueOf(j.role().get(0)));
            jedis.put("ping", j.ping());
            jedis.put("poolActive", jedisPool.getNumActive());
            jedis.put("poolIdle", jedisPool.getNumIdle());
        } catch (Exception e) {
            jedis.put("error", e.getClass().getSimpleName());
            jedis.put("errorMsg", e.getMessage());
            jedis.put("pitfall", "这就是'连接池耗尽/旧连接不可用'——面试题 Q2 的核心坑点");
        }
        r.put("jedis", jedis);

        // ---- Lettuce ----
        Map<String, Object> lettuce = new LinkedHashMap<>();
        lettuce.put("client", "Lettuce (Spring Boot 默认)");
        lettuce.put("discovery", "TopologyRefreshScheduler 自适应刷新——"
                + "收到 +switch-master 即时切换路由，无需重建池");
        if (lettuceFactory == null) initLettuce();
        try {
            lettuce.put("ping", lettuceFactory.getConnection().ping());
            lettuce.put("note", "NIO 单连接多路复用——不存在 Jedis 的池耗尽问题");
        } catch (Exception e) {
            lettuce.put("error", e.getClass().getSimpleName());
            lettuce.put("errorMsg", e.getMessage());
        }
        r.put("lettuce", lettuce);

        r.put("interviewHint",
                "对比 jedis.error / lettuce.error: 故障转移时谁报错、谁恢复快。"
                        + "这是面试官想听到的'我亲眼见过'");
        return r;
    }

    // ================================================================
    // 端点 2: 故障转移压力测试——反复用两种客户端去连，看谁扛得住
    // ================================================================

    /**
     * 连续 N 次用 Jedis 和 Lettuce 去 ping master。
     * 你在另一个终端 docker stop redis-master，然后立刻调这个接口——
     * 观察 jedisErrors vs lettuceErrors 的数量差异。
     */
    @PostMapping("/failover-test")
    public Map<String, Object> failoverTest(@RequestParam(defaultValue = "20") int times)
            throws InterruptedException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rounds", times);

        List<Map<String, Object>> rounds = new ArrayList<>();
        int jedisErrors = 0, lettuceErrors = 0;

        for (int i = 0; i < times; i++) {
            Map<String, Object> round = new LinkedHashMap<>();
            round.put("round", i + 1);

            // Jedis
            try (Jedis j = jedisPool.getResource()) {
                round.put("jedis", j.ping());
            } catch (Exception e) {
                round.put("jedis", "FAIL: " + e.getClass().getSimpleName());
                jedisErrors++;
            }

            // Lettuce
            try {
                round.put("lettuce", lettuceFactory.getConnection().ping());
            } catch (Exception e) {
                round.put("lettuce", "FAIL: " + e.getClass().getSimpleName());
                lettuceErrors++;
            }

            rounds.add(round);
            Thread.sleep(200); // 每轮 200ms 间隔
        }

        r.put("rounds", rounds);
        r.put("jedisErrors", jedisErrors);
        r.put("lettuceErrors", lettuceErrors);

        if (jedisErrors > lettuceErrors) {
            r.put("winner", "Lettuce");
            r.put("reason", "Jedis 连接池在故障转移时有'清空→重建'窗口，"
                    + "这期间借连接会失败。Lettuce 无池、路由即时切换，报错更少。");
        } else if (lettuceErrors > jedisErrors) {
            r.put("winner", "Jedis");
            r.put("reason", "异常——通常 Lettuce 应该更稳定，请检查网络");
        } else {
            r.put("winner", "平手——可能集群未发生切换，两者都能正常服务");
        }
        return r;
    }

    // ================================================================
    // 端点 3: 断路器
    // ================================================================

    @GetMapping("/circuit-breaker")
    public Map<String, Object> circuitBreaker() {
        Map<String, Object> r = new LinkedHashMap<>();
        int f = failures.get();
        r.put("failures", f);
        r.put("threshold", THRESHOLD);

        if (f >= THRESHOLD) {
            r.put("state", "OPEN");
            r.put("note", "所有 Redis 请求直接拒绝——走降级逻辑（如回源 DB 或返回缓存默认值）");
        } else {
            r.put("state", "CLOSED");
        }
        r.put("manual", "POST /api/q2/circuit-breaker/open 手动熔断  |  POST /api/q2/circuit-breaker/reset 复位");
        return r;
    }

    @PostMapping("/circuit-breaker/open")
    public Map<String, Object> forceOpen() {
        failures.set(THRESHOLD);
        log.warn("断路器 OPEN——模拟连续失败 {} 次，拒绝所有 Redis 请求", THRESHOLD);
        return circuitBreaker();
    }

    @PostMapping("/circuit-breaker/reset")
    public Map<String, Object> reset() {
        failures.set(0);
        return circuitBreaker();
    }

    // ---- init ----

    private void initJedis() {
        Set<String> sentinels = new HashSet<>();
        for (String s : sentinelNodes.split(",")) sentinels.add(s.trim());
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(8); cfg.setMaxIdle(4); cfg.setMinIdle(2);
        cfg.setMaxWaitMillis(3000);
        cfg.setTestOnBorrow(true);  // 【重点】借连接时校验有效性——降低 NoSuchElementException
        this.jedisPool = new JedisSentinelPool(masterName, sentinels, cfg, 3000, password);
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
        this.lettuceFactory.afterPropertiesSet(); // 【重点】启动 TopologyRefreshScheduler
    }

    @PreDestroy
    public void destroy() {
        if (jedisPool != null) jedisPool.close();
        if (lettuceFactory != null) lettuceFactory.destroy();
    }
}
