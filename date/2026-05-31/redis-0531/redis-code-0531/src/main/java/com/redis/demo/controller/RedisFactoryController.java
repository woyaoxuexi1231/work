package com.redis.demo.controller;

import com.redis.demo.factory.RedisClientFactory;
import com.redis.demo.factory.RedisMode;
import com.redis.demo.factory.RedisCommandTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Q7: 统一客户端工厂 —— HTTP 接口.
 *
 * <h3>对比实验路径</h3>
 * <pre>
 *   # 1. 初始化 Sentinel 模式
 *   curl -X POST 'http://localhost:8080/api/factory/init?mode=SENTINEL'
 *
 *   # 2. 查看当前模式
 *   curl http://localhost:8080/api/factory/mode
 *
 *   # 3. 执行命令（GET）
 *   curl 'http://localhost:8080/api/factory/execute?command=GET&key=test'
 *
 *   # 4. 执行命令（SET）
 *   curl -X POST 'http://localhost:8080/api/factory/execute?command=SET&key=hello&value=world'
 *
 *   # 5. 无中断切换到 Cluster 模式
 *   curl -X POST 'http://localhost:8080/api/factory/switch?mode=CLUSTER'
 *
 *   # 6. 重试测试（演示指数退避 + 断路器）
 *   curl -X POST 'http://localhost:8080/api/factory/retry-test?command=PING&maxRetries=3'
 *
 *   # 7. 断路器状态
 *   curl http://localhost:8080/api/factory/circuit-status
 *
 *   # 8. 手动复位断路器
 *   curl -X POST http://localhost:8080/api/factory/circuit-reset
 * </pre>
 */
@RestController
@RequestMapping("/api/factory")
public class RedisFactoryController {

    private final RedisClientFactory factory;
    private final RedisCommandTemplate template;

    public RedisFactoryController(RedisClientFactory factory, RedisCommandTemplate template) {
        this.factory = factory;
        this.template = template;
    }

    /**
     * 初始化指定模式.
     */
    @PostMapping("/init")
    public Map<String, Object> init(@RequestParam(defaultValue = "SENTINEL") RedisMode mode) {
        return factory.initDefault(mode);
    }

    /**
     * 【核心】无中断模式切换.
     *
     * 观察返回 JSON 中的 principle 字段——理解 AtomicReference 原子替换原理。
     */
    @PostMapping("/switch")
    public Map<String, Object> switchMode(@RequestParam RedisMode mode) {
        return factory.switchMode(mode);
    }

    /** 查看当前模式. */
    @GetMapping("/mode")
    public Map<String, Object> mode() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mode", factory.getCurrentMode());
        resp.put("executor", factory.getExecutor() != null
                ? factory.getExecutor().getConnectionInfo() : "uninitialized");
        resp.put("availableModes", new String[]{"SENTINEL", "CLUSTER"});
        resp.put("tip", "POST /api/factory/switch?mode=CLUSTER 可切换模式");
        return resp;
    }

    /**
     * 执行 Redis 命令.
     */
    @RequestMapping(value = "/execute", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> execute(@RequestParam String command,
                                       @RequestParam(required = false) String key,
                                       @RequestParam(required = false) String value) {
        List<String> args = new ArrayList<>();
        if (key != null) args.add(key);
        if (value != null) args.add(value);
        return template.executeWithRetry(command, args.toArray(new String[0]), 2);
    }

    /**
     * 【重点】重试测试——亲眼看到指数退避和断路器机制.
     */
    @PostMapping("/retry-test")
    public Map<String, Object> retryTest(@RequestParam(defaultValue = "PING") String command,
                                          @RequestParam(defaultValue = "3") int maxRetries) {
        return template.executeWithRetry(command, new String[0], maxRetries);
    }

    /** 断路器状态. */
    @GetMapping("/circuit-status")
    public Map<String, Object> circuitStatus() {
        return template.getCircuitStatus();
    }

    /** 复位断路器. */
    @PostMapping("/circuit-reset")
    public Map<String, Object> circuitReset() {
        return template.resetCircuit();
    }
}
