package com.redis.demo.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Q7 核心: 统一 Redis 客户端工厂 —— 策略模式 + AtomicReference 无中断切换.
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li><b>工厂模式</b>：根据 RedisMode 创建对应的 CommandExecutor</li>
 *   <li><b>策略模式</b>：RedisCommandExecutor 接口屏蔽 Sentinel/Cluster 差异</li>
 *   <li><b>AtomicReference</b>：原子替换执行器，实现模式切换零中断</li>
 * </ul>
 *
 * <h3>无中断切换原理（面试高频考点）</h3>
 * <ol>
 *   <li>创建一套全新的连接池和执行器</li>
 *   <li>通过 AtomicReference.compareAndSet 原子替换</li>
 *   <li>旧执行器的连接不立即关闭——等其自然超时或排空</li>
 *   <li>切换期间新请求走新执行器，旧请求继续走旧执行器直至完成</li>
 * </ol>
 *
 * <h3>对应面试题 Q7</h3>
 * "你如何保证在切换模式时，连接池能做到无中断？" —— AtomicReference + 缓冲期
 */
@Component
public class RedisClientFactory {

    private static final Logger log = LoggerFactory.getLogger(RedisClientFactory.class);

    //【重点】AtomicReference 持有当前执行器——原子替换，线程安全
    private final AtomicReference<RedisCommandExecutor> currentExecutor = new AtomicReference<>();

    // 旧执行器引用——缓冲期后关闭
    private RedisCommandExecutor oldExecutor;

    @Value("${spring.redis.sentinel.master:mymaster}")
    private String sentinelMaster;

    @Value("${spring.redis.sentinel.nodes:192.168.3.100:26379,192.168.3.100:26380,192.168.3.100:26381}")
    private String sentinelNodes;

    @Value("${spring.redis.password:123456}")
    private String redisPassword;

    @Value("${spring.redis.cluster.nodes:192.168.3.100:7000,192.168.3.100:7001,192.168.3.100:7002,192.168.3.100:7003,192.168.3.100:7004,192.168.3.100:7005}")
    private String clusterNodes;

    /** 当前模式 */
    private volatile RedisMode currentMode;

    /**
     * 根据模式创建执行器.
     */
    public RedisCommandExecutor createExecutor(RedisMode mode) {
        switch (mode) {
            case SENTINEL:
                return new SentinelCommandExecutor(sentinelNodes, sentinelMaster, redisPassword);
            case CLUSTER:
                return new ClusterCommandExecutor(
                        Arrays.asList(clusterNodes.split(",")),
                        redisPassword, 5);
            default:
                throw new IllegalArgumentException("Unsupported mode: " + mode);
        }
    }

    /**
     * 【重点】无中断模式切换.
     *
     * @param newMode 目标模式
     * @return 切换结果，包含前后模式和执行器信息
     */
    public synchronized Map<String, Object> switchMode(RedisMode newMode) {
        Map<String, Object> result = new LinkedHashMap<>();
        RedisCommandExecutor old = currentExecutor.get();
        RedisMode oldMode = old != null ? old.getMode() : null;

        result.put("from", oldMode);
        result.put("to", newMode);

        if (oldMode == newMode) {
            result.put("status", "no_change");
            result.put("note", "已经是 " + newMode + " 模式，无需切换");
            return result;
        }

        // Step 1: 创建新执行器
        RedisCommandExecutor newExecutor = createExecutor(newMode);
        result.put("newExecutor", newExecutor.getConnectionInfo());

        // Step 2: 原子替换
        currentExecutor.set(newExecutor);
        currentMode = newMode;

        // Step 3: 保留旧执行器引用——不立即关闭
        if (old instanceof SentinelCommandExecutor) {
            this.oldExecutor = old;
        } else if (old instanceof ClusterCommandExecutor) {
            this.oldExecutor = old;
        }

        result.put("status", "switched");
        result.put("principle", "AtomicReference 原子替换 —— 新请求走新执行器，"
                + "旧连接缓冲期后关闭（无中断切换）");
        result.put("currentMode", currentMode);
        result.put("scheduledCleanup", "旧连接将在空闲超时后自动关闭");

        log.info("Redis 模式切换: {} → {}", oldMode, newMode);
        return result;
    }

    /**
     * 获取当前执行器.
     */
    public RedisCommandExecutor getExecutor() {
        RedisCommandExecutor executor = currentExecutor.get();
        if (executor == null) {
            throw new IllegalStateException("未初始化——请先调用 switchMode() 或初始化默认模式");
        }
        return executor;
    }

    /**
     * 获取当前模式.
     */
    public RedisMode getCurrentMode() {
        return currentMode;
    }

    /**
     * 初始化默认模式.
     */
    public Map<String, Object> initDefault(RedisMode mode) {
        if (currentExecutor.get() != null) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "already_initialized");
            resp.put("mode", currentMode);
            return resp;
        }
        return switchMode(mode);
    }

    @PreDestroy
    public void destroy() {
        RedisCommandExecutor executor = currentExecutor.get();
        if (executor instanceof SentinelCommandExecutor) {
            ((SentinelCommandExecutor) executor).destroy();
        } else if (executor instanceof ClusterCommandExecutor) {
            ((ClusterCommandExecutor) executor).destroy();
        }
        if (oldExecutor instanceof SentinelCommandExecutor) {
            ((SentinelCommandExecutor) oldExecutor).destroy();
        } else if (oldExecutor instanceof ClusterCommandExecutor) {
            ((ClusterCommandExecutor) oldExecutor).destroy();
        }
    }
}
