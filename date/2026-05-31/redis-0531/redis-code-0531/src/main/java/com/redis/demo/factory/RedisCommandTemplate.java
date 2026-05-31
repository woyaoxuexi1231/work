package com.redis.demo.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Q7: RedisTemplate —— 带重试、断路器、拓扑刷新的命令执行模板.
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>指数退避重试</b>：失败后等待 2^n 秒再重试，避免雪崩</li>
 *   <li><b>断路器</b>：连续失败 N 次熔断，保护下游</li>
 *   <li><b>拓扑刷新隔离</b>：刷新失败不影响业务线程</li>
 *   <li><b>幂等判断</b>：区分可重试异常与不可重试异常</li>
 * </ul>
 *
 * <h3>对应面试题 Q7 追问</h3>
 * "怎么实现命令执行的超时控制和重试时的 idempotent 判断？"
 * "拓扑刷新如果失败，会影响到业务线程吗？"
 */
@Component
public class RedisCommandTemplate {

    private static final Logger log = LoggerFactory.getLogger(RedisCommandTemplate.class);

    private final RedisClientFactory factory;

    // 断路器
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenTime = 0;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int CIRCUIT_THRESHOLD = 5;
    private static final long CIRCUIT_TIMEOUT_MS = 10_000;

    // 重试期间收集的诊断信息
    private final List<Map<String, Object>> retryLog = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_RETRY_LOG = 50;

    public RedisCommandTemplate(RedisClientFactory factory) {
        this.factory = factory;
    }

    /**
     * 【重点】带重试和断路器的命令执行.
     *
     * 这是面试时你要写出伪代码的核心方法！
     *
     * 流程：
     * 1. 检查断路器——OPEN 则直接拒绝
     * 2. 获取连接 → 执行命令
     * 3. 失败 → 判断是否可重试 → 指数退避 → 重试
     * 4. 连续失败超过阈值 → 熔断
     *
     * @param command    命令名（GET/SET/PING）
     * @param args       命令参数
     * @param maxRetries 最大重试次数
     */
    public Map<String, Object> executeWithRetry(String command, String[] args, int maxRetries) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("command", command);
        result.put("args", args);

        // 1. 断路器检查
        if (circuitOpen) {
            long elapsed = System.currentTimeMillis() - circuitOpenTime;
            if (elapsed < CIRCUIT_TIMEOUT_MS) {
                result.put("status", "CIRCUIT_OPEN");
                result.put("note", "断路器 OPEN——直接拒绝，不重试。剩余 "
                        + (CIRCUIT_TIMEOUT_MS - elapsed) + "ms");
                return result;
            } else {
                //【重点】超时后进入 HALF_OPEN
                circuitOpen = false;
                result.put("circuitState", "HALF_OPEN");
                log.info("断路器进入 HALF_OPEN——下一个请求将试探性执行");
            }
        }

        // 2. 带重试的执行
        int attempts = 0;
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> attemptLog = new ArrayList<>();

        while (attempts <= maxRetries) {
            attempts++;
            try {
                RedisCommandExecutor executor = factory.getExecutor();
                Object value = executor.execute(command, args);

                // 成功 → 重置计数器
                consecutiveFailures.set(0);
                result.put("status", "SUCCESS");
                result.put("value", value);
                result.put("attempts", attempts);
                result.put("elapsedMs", System.currentTimeMillis() - startTime);
                result.put("attemptsDetail", attemptLog);
                return result;

            } catch (Exception e) {
                Map<String, Object> attempt = new LinkedHashMap<>();
                attempt.put("attempt", attempts);
                attempt.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());

                //【重点】判断是否可重试——网络类异常可重试，数据类异常不可重试
                if (!isRetriable(e)) {
                    attempt.put("retriable", false);
                    attempt.put("reason", "非重试异常（数据错误/业务异常）→ 直接抛出");
                    attemptLog.add(attempt);
                    result.put("status", "FAILED_NON_RETRIABLE");
                    result.put("attempts", attempts);
                    result.put("attemptsDetail", attemptLog);
                    return result;
                }

                attempt.put("retriable", true);
                attemptLog.add(attempt);

                if (attempts > maxRetries) {
                    // 重试耗尽
                    recordFailure();
                    result.put("status", "EXHAUSTED");
                    result.put("attempts", attempts);
                    result.put("attemptsDetail", attemptLog);
                    result.put("note", "重试 " + maxRetries + " 次后仍然失败");
                    return result;
                }

                //【重点】指数退避: 2^attempt * 100ms → 200ms, 400ms, 800ms...
                long backoffMs = (long) Math.pow(2, attempts) * 100;
                attempt.put("backoffMs", backoffMs);

                //【重点】异步触发拓扑刷新——不阻塞当前重试
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("异步触发拓扑刷新（不阻塞业务线程）");
                        factory.getExecutor().execute("PING");
                    } catch (Exception ignored) {
                        log.warn("拓扑刷新失败——不影响业务线程");
                    }
                });

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        recordFailure();
        result.put("status", "EXHAUSTED");
        return result;
    }

    /**
     * 【重点】判断异常是否可重试.
     *
     * 可重试：网络超时、连接断开、MOVED/ASK 重定向、READONLY
     * 不可重试：数据类型错误、Lua 脚本错误、CROSSSLOT
     */
    private boolean isRetriable(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String className = e.getClass().getSimpleName();

        // Redis 连接类异常 → 可重试
        if (className.contains("RedisConnection")
                || className.contains("RedisRetry")
                || className.contains("Timeout")) {
            return true;
        }
        // READONLY → 可重试（重新获取主连接后就能写入）
        if (msg.contains("readonly")) return true;
        // MOVED/ASK → 可重试（Lettuce 自动处理，此处兜底）
        if (msg.contains("moved") || msg.contains("ask")) return true;
        // Connection refused → 可重试
        if (msg.contains("connection refused")) return true;

        // 数据类错误 → 不可重试
        if (msg.contains("crossslot")) return false;
        if (msg.contains("wrongtype")) return false;
        if (msg.contains("noscript")) return false;

        // 默认可重试（保守策略）
        return true;
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_THRESHOLD) {
            circuitOpen = true;
            circuitOpenTime = System.currentTimeMillis();
            log.warn("连续失败 {} 次 ≥ 阈值 {} → 断路器 OPEN",
                    failures, CIRCUIT_THRESHOLD);
        }
    }

    // ========================================================================
    // 监控 & 诊断
    // ========================================================================

    /** 断路器状态. */
    public Map<String, Object> getCircuitStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        if (circuitOpen) {
            long remaining = CIRCUIT_TIMEOUT_MS - (System.currentTimeMillis() - circuitOpenTime);
            s.put("state", remaining > 0 ? "OPEN" : "HALF_OPEN");
            s.put("remainingMs", Math.max(0, remaining));
        } else {
            s.put("state", "CLOSED");
        }
        s.put("consecutiveFailures", consecutiveFailures.get());
        s.put("threshold", CIRCUIT_THRESHOLD);
        s.put("timeoutMs", CIRCUIT_TIMEOUT_MS);
        return s;
    }

    /** 重试日志. */
    public List<Map<String, Object>> getRetryLog() {
        return new ArrayList<>(retryLog);
    }

    /** 手动复位断路器. */
    public Map<String, Object> resetCircuit() {
        circuitOpen = false;
        consecutiveFailures.set(0);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("status", "reset");
        s.put("state", "CLOSED");
        return s;
    }
}
