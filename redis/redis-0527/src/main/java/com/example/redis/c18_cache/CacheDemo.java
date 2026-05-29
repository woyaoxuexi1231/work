package com.example.redis.c18_cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 18. 缓存设计最佳实践
 * <p>
 * 缓存的三大问题：
 * <p>
 * 1. 缓存穿透（Cache Penetration）：
 *    查询不存在的数据，缓存未命中，每次都查数据库。
 *    解决方案：
 *    - 空值缓存：将 null 结果也缓存（TTL 较短）
 *    - 布隆过滤器：快速判断 key 是否可能存在
 *    - 参数校验：拦截非法请求
 * <p>
 * 2. 缓存击穿（Cache Breakdown）：
 *    热点 key 过期瞬间，大量请求同时查数据库。
 *    解决方案：
 *    - 互斥锁：只允许一个请求重建缓存
 *    - 逻辑过期：缓存永不过期，value 中存储过期时间
 *    - 热点数据预加载
 * <p>
 * 3. 缓存雪崩（Cache Avalanche）：
 *    大量 key 同时过期，或 Redis 宕机，请求全部打到数据库。
 *    解决方案：
 *    - 过期时间随机化
 *    - 多级缓存（本地缓存 + Redis）
 *    - 高可用集群（Sentinel / Cluster）
 *    - 降级限流
 * <p>
 * 缓存与数据库一致性：
 * - 先更新数据库，再删除缓存（推荐）
 * - 延时双删：更新 DB → 删缓存 → 延时 → 再删缓存
 * - 订阅 binlog：通过 Canal 等中间件同步更新缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 缓存穿透解决方案：空值缓存
     * <p>
     * 将查询结果为 null 的情况也缓存起来，
     * 设置较短的 TTL（如 2 分钟），防止恶意请求穿透到数据库。
     */
    public String cachePenetration() {
        String cacheKey = "cache:user:99999";

        // 模拟查询：先查缓存，未命中则查数据库
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            log.info("[穿透防护] 命中缓存: {}", cached);
            return "命中: " + cached;
        }

        // 模拟数据库查询
        String dbResult = null; // 数据库中不存在

        if (dbResult == null) {
            // 空值缓存：缓存空结果，TTL 2 分钟
            redisTemplate.opsForValue().set(cacheKey, "", 2, TimeUnit.MINUTES);
            log.info("[穿透防护] 数据库未命中, 缓存空值");
            return "未命中(已缓存空值)";
        }

        redisTemplate.opsForValue().set(cacheKey, dbResult, 30, TimeUnit.MINUTES);
        return "从DB加载: " + dbResult;
    }

    /**
     * 缓存击穿解决方案：互斥锁
     * <p>
     * 当缓存过期时，只允许一个请求去数据库加载数据，
     * 其他请求等待或返回旧数据。
     */
    public String cacheBreakdown() {
        String cacheKey = "cache:hot:product:1001";
        String lockKey = "cache:lock:product:1001";
        String lockValue = java.util.UUID.randomUUID().toString();

        // 查缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return "命中: " + cached;
        }

        // 缓存未命中，尝试获取锁
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 获得锁，查询数据库
                String dbResult = "product_data_from_db";
                redisTemplate.opsForValue().set(cacheKey, dbResult, 30, TimeUnit.MINUTES);
                log.info("[击穿防护] 获得锁, 从DB加载数据");
                return "从DB加载: " + dbResult;
            } finally {
                // 释放锁：仅当 value 匹配时才删除（防止误删其他线程的锁）
                String unlockScript = "if redis.call('get', KEYS[1]) == ARGV[1] then\n"
                        + "    return redis.call('del', KEYS[1])\n"
                        + "else\n"
                        + "    return 0\n"
                        + "end";
                redisTemplate.execute(new DefaultRedisScript<>(unlockScript, Long.class),
                        Collections.singletonList(lockKey), lockValue);
            }
        } else {
            // 未获得锁，短暂等待后重试
            log.info("[击穿防护] 未获得锁, 等待重试");
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            cached = redisTemplate.opsForValue().get(cacheKey);
            return cached != null ? "等待后命中: " + cached : "降级返回";
        }
    }

    /**
     * 缓存雪崩解决方案：过期时间随机化
     * <p>
     * 给缓存的过期时间加上随机值，
     * 避免大量 key 同时过期。
     */
    public String cacheAvalanche() {
        java.util.Random random = new java.util.Random();
        int baseTTL = 30; // 基础 TTL 30 分钟
        int randomRange = 10; // 随机范围 0~10 分钟

        for (int i = 0; i < 5; i++) {
            String key = "cache:product:" + i;
            int ttl = baseTTL + random.nextInt(randomRange);
            redisTemplate.opsForValue().set(key, "data:" + i, ttl, TimeUnit.MINUTES);
            log.info("[雪崩防护] key={}, TTL={}min", key, ttl);
        }

        // 清理
        for (int i = 0; i < 5; i++) {
            redisTemplate.delete("cache:product:" + i);
        }

        return "过期时间随机化演示完成";
    }

    /**
     * Spring Cache 注解演示
     * <p>
     * @Cacheable: 查询时先查缓存，未命中则执行方法并缓存结果
     * @CachePut: 每次执行方法并更新缓存
     * @CacheEvict: 删除缓存
     */
    @Cacheable(value = "users", key = "#userId")
    public String getUserById(String userId) {
        // 模拟数据库查询
        log.info("[Spring Cache] 从数据库查询用户: {}", userId);
        return "User:" + userId;
    }

    @CachePut(value = "users", key = "#userId")
    public String updateUser(String userId, String newName) {
        // 模拟数据库更新
        log.info("[Spring Cache] 更新数据库: userId={}, name={}", userId, newName);
        return newName;
    }

    @CacheEvict(value = "users", key = "#userId")
    public void deleteUser(String userId) {
        log.info("[Spring Cache] 删除用户缓存: {}", userId);
    }
}
