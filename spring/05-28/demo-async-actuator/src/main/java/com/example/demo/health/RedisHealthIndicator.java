package com.example.demo.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.stereotype.Component;

/**
 * 自定义 Redis 健康指示器
 *
 * 原理：
 * HealthIndicator 接口只有一个方法 health()，返回 Health 对象。
 * Spring Boot Actuator 会自动发现所有 HealthIndicator Bean，
 * 聚合到 /actuator/health 端点返回。
 *
 * 这里模拟 Redis 连接检查：
 * - 如果 RedisConnectionFactory 可用，则尝试真实 ping
 * - 提供一个静态开关 simulateHealth，方便演示 UP/DOWN 切换
 */
@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthIndicator.class);

    /**
     * 演示开关 —— 通过 Controller 切换，模拟 Redis 宕机/恢复
     */
    public static volatile boolean simulateUp = true;

    /**
     * 不想真连 Redis 时，设为 false 会让真实 ping 被跳过
     */
    public static volatile boolean tryRealRedis = false;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        // 优先尝试真实 Redis 连接（仅在配置了 Redis 且开关打开时）
        if (tryRealRedis && redisConnectionFactory != null) {
            return realRedisHealth();
        }

        // 模拟模式：根据开关返回 UP 或 DOWN
        if (simulateUp) {
            return Health.up()
                    .withDetail("redis", "模拟连接正常")
                    .withDetail("host", "127.0.0.1:6379 (模拟)")
                    .build();
        } else {
            return Health.down()
                    .withDetail("redis", "模拟连接失败 —— 无法连接到 Redis")
                    .withDetail("error", "Connection refused (模拟)")
                    .build();
        }
    }

    /**
     * 通过 RedisConnectionFactory 真实 ping
     */
    private Health realRedisHealth() {
        RedisConnection connection = null;
        try {
            connection = redisConnectionFactory.getConnection();
            String pong = connection.ping();
            if ("PONG".equals(pong)) {
                return Health.up()
                        .withDetail("redis", "PING 成功")
                        .withDetail("response", pong)
                        .build();
            }
            return Health.down()
                    .withDetail("redis", "PING 返回异常: " + pong)
                    .build();
        } catch (Exception e) {
            log.error("Redis 健康检查失败", e);
            return Health.down()
                    .withDetail("redis", "连接失败: " + e.getMessage())
                    .withDetail("exception", e.getClass().getSimpleName())
                    .build();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
