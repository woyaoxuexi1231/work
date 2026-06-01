package com.redis.demo.q2_sentinel_client;

import com.redis.demo.q2_sentinel_client.SentinelClientService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Q2: Java 客户端与 Sentinel 集成 —— Jedis vs Lettuce 对比.
 *
 * <h3>对比实验路径</h3>
 * <pre>
 *   # 1. 初始化 Jedis 连接池
 *   curl -X POST http://localhost:8080/api/sentinel/client/jedis-init
 *
 *   # 2. 模拟连接池耗尽（发送 50 个并发请求）
 *   curl -X POST 'http://localhost:8080/api/sentinel/client/jedis-exhaust?count=50'
 *
 *   # 3. 模拟 READONLY 错误场景
 *   curl http://localhost:8080/api/sentinel/client/readonly-sim
 *
 *   # 4. 初始化 Lettuce 连接
 *   curl -X POST http://localhost:8080/api/sentinel/client/lettuce-init
 *
 *   # 5. 对比两种客户端
 *   curl http://localhost:8080/api/sentinel/client/compare
 *
 *   # 6. 查看断路器状态
 *   curl http://localhost:8080/api/sentinel/client/circuit-breaker
 *
 *   # 7. 手动触发熔断（观察断路器 OPEN 后的行为）
 *   curl -X POST http://localhost:8080/api/sentinel/client/circuit-breaker/open
 * </pre>
 */
@RestController
@RequestMapping("/api/sentinel/client")
public class SentinelClientController {

    private final SentinelClientService service;

    public SentinelClientController(SentinelClientService service) {
        this.service = service;
    }

    /** 初始化 JedisSentinelPool. */
    @PostMapping("/jedis-init")
    public Map<String, Object> jedisInit() {
        return service.initJedisPool();
    }

    /**
     * 【重点】模拟连接池耗尽 —— 亲眼看到 NoSuchElementException.
     *
     * @param count 并发请求数（默认 50）
     */
    @PostMapping("/jedis-exhaust")
    public Map<String, Object> jedisExhaust(@RequestParam(defaultValue = "50") int count) {
        return service.simulatePoolExhaustion(count);
    }

    /** 模拟 READONLY 错误场景. */
    @GetMapping("/readonly-sim")
    public Map<String, Object> readonlySim() {
        return service.simulateReadonlyScenario();
    }

    /** 初始化 Lettuce Sentinel 连接. */
    @PostMapping("/lettuce-init")
    public Map<String, Object> lettuceInit() {
        return service.initLettuceConnection();
    }

    /** 对比 Jedis vs Lettuce. */
    @GetMapping("/compare")
    public Map<String, Object> compare() {
        return service.compareClients();
    }

    /** 查看断路器状态. */
    @GetMapping("/circuit-breaker")
    public Map<String, Object> circuitBreaker() {
        return service.getCircuitBreakerStatus();
    }

    /** 手动触发熔断. */
    @PostMapping("/circuit-breaker/open")
    public Map<String, Object> openCircuit() {
        return service.forceOpenCircuit();
    }

    /** 复位断路器. */
    @PostMapping("/circuit-breaker/reset")
    public Map<String, Object> resetCircuit() {
        return service.resetCircuit();
    }
}
