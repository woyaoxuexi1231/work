package com.redis.demo.sentinel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Q2: Sentinel 客户端集成 —— Jedis vs Lettuce 对比.
 *
 * <h3>核心对比</h3>
 * <table>
 *   <tr><th>维度</th><th>JedisSentinelPool</th><th>Lettuce</th></tr>
 *   <tr><td>连接模型</td><td>池化 BIO，借-还模式</td><td>多路复用 NIO，单连接即够</td></tr>
 *   <tr><td>拓扑感知</td><td>主动问路 + 被动订阅</td><td>自适应拓扑刷新（内置 TopologyRefresh）</td></tr>
 *   <tr><td>切换响应</td><td>旧连接报错 → 重建池</td><td>收到 +switch-master → 即时切换</td></tr>
 *   <tr><td>常见坑</td><td>NoSuchElementException / READONLY</td><td>连接断连期间的超时等待</td></tr>
 * </table>
 *
 * <h3>对应面试题 Q2</h3>
 * <ul>
 *   <li>JedisSentinelPool 如何知道当前主是谁？→ 轮询 Sentinel + 订阅频道</li>
 *   <li>切换过程中连续报 READONLY 是什么原因？→ 旧主复活成 Slave，旧连接仍指向它</li>
 *   <li>连接池耗尽怎么兜底？→ 重试 + 断路器 + 降级</li>
 * </ul>
 */
@Service
public class SentinelClientService {

    private static final Logger log = LoggerFactory.getLogger(SentinelClientService.class);

    @Value("${spring.redis.sentinel.nodes:192.168.3.100:26379,192.168.3.100:26380,192.168.3.100:26381}")
    private String sentinelNodes;

    @Value("${spring.redis.sentinel.master:mymaster}")
    private String sentinelMaster;

    @Value("${spring.redis.password:123456}")
    private String redisPassword;

    private JedisSentinelPool jedisPool;
    private LettuceConnectionFactory lettuceFactory;
    private StringRedisTemplate lettuceTemplate;

    // 断路器状态（手写，不引入 Resilience4j——保持依赖精简）
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenTime = 0;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int CIRCUIT_THRESHOLD = 5;     // 连续失败 5 次 → 熔断
    private static final long CIRCUIT_TIMEOUT_MS = 10000; // 熔断 10 秒

    @PostConstruct
    public void init() {
        log.info("初始化 Sentinel 客户端...");
    }

    // ========================================================================
    // JedisSentinelPool
    // ========================================================================

    /**
     * 【重点】初始化 JedisSentinelPool.
     *
     * 内部机制：
     * 1. 连接任意一个 Sentinel，执行 SENTINEL get-master-addr-by-name 获取当前主地址
     * 2. 建立到主节点的连接池
     * 3. 订阅 +switch-master 频道，切换发生时重建连接池
     *
     * 常见坑：
     * - 初始化时如果所有 Sentinel 都不可达，构造直接抛异常
     * - 主从切换瞬间，池里还缓存着指向旧主的连接 → 请求失败
     */
    public synchronized Map<String, Object> initJedisPool() {
        Set<String> sentinels = parseSentinels();
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);
        poolConfig.setMinIdle(2);
        poolConfig.setMaxWaitMillis(3000);
        //【重点】借连接时校验有效性——减少 NoSuchElementException
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        jedisPool = new JedisSentinelPool(sentinelMaster, sentinels,
                poolConfig, 3000, redisPassword);

        Map<String, Object> result = new HashMap<>();
        result.put("client", "JedisSentinelPool");
        result.put("sentinelNodes", sentinelNodes);
        result.put("masterName", sentinelMaster);
        result.put("poolConfig", "maxTotal=8, testOnBorrow=true");

        try (Jedis jedis = jedisPool.getResource()) {
            result.put("currentMaster", jedis.getClient().getHost()
                    + ":" + jedis.getClient().getPort());
            result.put("status", "connected");
            //【重点】验证当前主确实是主
            result.put("role", jedis.role().get(0));
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 【重点】模拟 JedisSentinelPool 连接池耗尽场景.
     *
     * 线上真实情况: 主库宕机 → 连接池里全是旧主连接 → 线程疯狂借连接 → 耗尽 → NoSuchElementException
     *
     * 调用此接口后观察返回的 errors 数量和被拒绝的请求数
     */
    public Map<String, Object> simulatePoolExhaustion(int requestCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("client", "JedisSentinelPool");
        result.put("simulatedRequests", requestCount);

        List<String> errors = new ArrayList<>();
        int success = 0;

        for (int i = 0; i < requestCount; i++) {
            try (Jedis jedis = jedisPool.getResource()) {
                String pong = jedis.ping();
                if ("PONG".equals(pong)) success++;
            } catch (Exception e) {
                //【重点】这里就是线上最常见的 NoSuchElementException
                errors.add("请求#" + i + ": " + e.getClass().getSimpleName()
                        + " - " + e.getMessage());
            }
        }

        result.put("success", success);
        result.put("errors", errors.size());
        result.put("errorDetails", errors.subList(0, Math.min(10, errors.size())));
        if (errors.isEmpty()) {
            result.put("verdict", "连接池工作正常，所有请求成功");
        } else {
            result.put("verdict", "出现 " + errors.size() + " 次连接获取失败——"
                    + "这正是主从切换后连接池未及时刷新的典型症状");
        }
        return result;
    }

    /**
     * 【重点】模拟 READONLY 错误.
     *
     * 场景: 主库宕机后短暂复活成 Slave → 持有旧连接的客户端尝试写入 → Redis 返回 READONLY
     *
     * 缓解: 捕获 READONLY → 强制刷新拓扑 → 重试
     */
    public Map<String, Object> simulateReadonlyScenario() {
        Map<String, Object> result = new HashMap<>();
        result.put("scenario", "旧主复活成 Slave → 旧连接写入 → READONLY");

        List<Map<String, Object>> steps = new ArrayList<>();

        // Step 1: 获取当前主连接
        steps.add(step("1", "获取当前主连接", "正常——连接指向 Master"));
        // Step 2: 模拟主宕机 + Sentinel 切换（不真做，只描述）
        steps.add(step("2", "主节点宕机，Sentinel 切换到新主",
                "旧主降为 Slave，新主升级"));
        // Step 3: 旧连接尝试写入
        steps.add(step("3", "旧连接（仍指向旧主）尝试写入",
                "READONLY You can't write against a read only slave"));
        // Step 4: 客户端感知 + 刷新拓扑
        steps.add(step("4", "客户端捕获 READONLY → 拓扑刷新",
                "重新通过 SENTINEL get-master-addr-by-name 获取新主"));
        // Step 5: 重试
        steps.add(step("5", "用新主连接重试写入",
                "成功——新主接受写入"));

        result.put("steps", steps);
        result.put("mitigation", "1. 捕获 READONLY 异常 "
                + "2. 强制触发拓扑刷新（JedisSentinelPool 内部订阅 +switch-master） "
                + "3. 业务层结合断路器（见下方 circuit-breaker 接口）");
        return result;
    }

    // ========================================================================
    // Lettuce（Spring Data Redis）
    // ========================================================================

    /**
     * 【重点】初始化 Lettuce Sentinel 连接.
     *
     * Lettuce 的天然优势：
     * - 基于 Netty NIO，单连接即可处理高并发
     * - 内置 TopologyRefreshScheduler，收到 +switch-master 自动切换
     * - 无需像 JedisSentinelPool 那样管理连接池生命周期
     */
    public synchronized Map<String, Object> initLettuceConnection() {
        if (lettuceFactory != null) {
            lettuceFactory.destroy();
        }

        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
        sentinelConfig.setMaster(sentinelMaster);
        for (String node : sentinelNodes.split(",")) {
            String[] parts = node.trim().split(":");
            sentinelConfig.sentinel(parts[0], Integer.parseInt(parts[1]));
        }
        sentinelConfig.setPassword(redisPassword);

        lettuceFactory = new LettuceConnectionFactory(sentinelConfig);
        lettuceFactory.afterPropertiesSet(); //【重点】启动拓扑刷新调度器
        lettuceTemplate = new StringRedisTemplate(lettuceFactory);

        Map<String, Object> result = new HashMap<>();
        result.put("client", "Lettuce (Spring Data Redis)");
        result.put("sentinelNodes", sentinelNodes);
        result.put("masterName", sentinelMaster);
        result.put("advantages", new String[]{
                "NIO 多路复用——单连接高并发",
                "自适应拓扑刷新——收到 +switch-master 即时切换",
                "连接生命周期自动管理——无需手写连接池校验逻辑"
        });

        try {
            String pong = lettuceTemplate.getConnectionFactory()
                    .getConnection().ping();
            result.put("status", "connected");
            result.put("ping", pong);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 对比 Jedis 与 Lettuce 在 Sentinel 模式下的差异.
     */
    public Map<String, Object> compareClients() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "JedisSentinelPool vs Lettuce — Sentinel 模式对比");

        Map<String, String> jedis = new LinkedHashMap<>();
        jedis.put("模型", "BIO 连接池（借-还）");
        jedis.put("拓扑发现", "SENTINEL get-master-addr-by-name + 订阅 +switch-master");
        jedis.put("切换行为", "订阅收到 +switch-master → 清空池 → 重建");
        jedis.put("切换窗口", "有短暂不可用窗口（旧连接尚未清空）");
        jedis.put("常见异常", "NoSuchElementException / READONLY / Connection refused");
        jedis.put("线程安全", "Jedis 实例非线程安全，需池化隔离");
        result.put("jedis", jedis);

        Map<String, String> lettuce = new LinkedHashMap<>();
        lettuce.put("模型", "NIO 多路复用（Netty）");
        lettuce.put("拓扑发现", "主动 CLUSTER SLOTS + 被动 Pub/Sub");
        lettuce.put("切换行为", "TopologyRefreshScheduler 自适应刷新");
        lettuce.put("切换窗口", "接近无缝（收到事件即切换路由）");
        lettuce.put("常见异常", "RedisConnectionException / Timeout");
        lettuce.put("线程安全", "StatefulRedisConnection 线程安全，天然并发");
        result.put("lettuce", lettuce);

        result.put("推荐", "生产环境优先选 Lettuce——Spring Boot 2.x 默认，无需额外管理池生命周期");
        return result;
    }

    // ========================================================================
    // 断路器（手写，无外部依赖）
    // ========================================================================

    /**
     * 【重点】手写断路器——不引入 Resilience4j，保持依赖精简的同时教你原理.
     *
     * 三种状态：
     * - CLOSED: 正常，请求直接放行
     * - OPEN:   熔断，直接拒绝所有 Redis 请求，走降级逻辑
     * - HALF_OPEN: 试探性放行一个请求，成功则 CLOSED，失败则继续 OPEN
     *
     * 对应面试题 Q2 追问：切换过程中客户端连续重试失败，如何兜底？
     */
    public Map<String, Object> getCircuitBreakerStatus() {
        Map<String, Object> result = new LinkedHashMap<>();

        if (circuitOpen) {
            long elapsed = System.currentTimeMillis() - circuitOpenTime;
            if (elapsed > CIRCUIT_TIMEOUT_MS) {
                //【重点】熔断超时，进入 HALF_OPEN
                circuitOpen = false;
                result.put("state", "HALF_OPEN");
                result.put("note", "熔断时间已过 " + CIRCUIT_TIMEOUT_MS
                        + "ms，下一个请求将试探性放行");
            } else {
                result.put("state", "OPEN");
                result.put("note", "熔断中，剩余 " + (CIRCUIT_TIMEOUT_MS - elapsed) + "ms");
            }
        } else {
            result.put("state", "CLOSED");
            result.put("note", "断路器关闭，请求正常放行");
        }

        result.put("consecutiveFailures", consecutiveFailures.get());
        result.put("threshold", CIRCUIT_THRESHOLD);
        result.put("timeoutMs", CIRCUIT_TIMEOUT_MS);
        result.put("principle",
                "连续失败 " + CIRCUIT_THRESHOLD + " 次 → 熔断 "
                        + (CIRCUIT_TIMEOUT_MS / 1000) + " 秒 →"
                        + " 半开试探 → 成功恢复 / 失败继续熔断");
        return result;
    }

    /**
     * 手动触发熔断（演示用）.
     */
    public Map<String, Object> forceOpenCircuit() {
        circuitOpen = true;
        circuitOpenTime = System.currentTimeMillis();
        consecutiveFailures.set(CIRCUIT_THRESHOLD);
        log.warn("断路器手动打开——模拟 Redis 不可达时的保护机制");
        return getCircuitBreakerStatus();
    }

    /**
     * 手动恢复断路器.
     */
    public Map<String, Object> resetCircuit() {
        circuitOpen = false;
        consecutiveFailures.set(0);
        log.info("断路器已复位");
        return getCircuitBreakerStatus();
    }

    // ---- helpers -----------------------------------------------------------

    private Set<String> parseSentinels() {
        Set<String> set = new HashSet<>();
        for (String node : sentinelNodes.split(",")) {
            set.add(node.trim());
        }
        return set;
    }

    private Map<String, Object> step(String id, String action, String result) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("step", id);
        s.put("action", action);
        s.put("result", result);
        return s;
    }

    @PreDestroy
    public void destroy() {
        if (jedisPool != null) jedisPool.close();
        if (lettuceFactory != null) lettuceFactory.destroy();
    }
}
