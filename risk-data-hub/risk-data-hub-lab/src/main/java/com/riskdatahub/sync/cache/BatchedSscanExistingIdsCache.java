package com.riskdatahub.sync.cache;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分批加载 Redis Map 策略 — 存储 sourceRowId → globalId 映射。
 * <p>
 * 改用 {@code RMap} 替代 RSet，key 为 sourceRowId，value 为 globalId，
 * 避免 update 分支再次查库获取 globalId。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sync.cache.strategy", havingValue = "batched-sscan")
public class BatchedSscanExistingIdsCache implements ExistingIdsCache {

    /** SSCAN 每批扫描数量 */
    private static final int SCAN_BATCH_SIZE = 1000;

    private final RedissonClient redissonClient;

    public BatchedSscanExistingIdsCache(RedissonClient redissonClient) {
        log.info("[BatchedMap] 启动");
        this.redissonClient = redissonClient;
    }

    /**
     * 获取已存在的 sourceRowId → globalId 映射。
     */
    public Map<Long, Long> getExistingIds(String cacheKey, Supplier<Map<Long, Long>> dbLoader) {
        RMap<Long, Long> cached = redissonClient.getMap(cacheKey);

        if (cached.isExists()) {
            long start = System.currentTimeMillis();
            Map<Long, Long> result = new HashMap<>();
            for (Map.Entry<Long, Long> entry : cached.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
                if (result.size() % SCAN_BATCH_SIZE == 0) {
                    log.debug("[BatchedMap] 已扫描 {} 个元素, cacheKey={}", result.size(), cacheKey);
                }
            }
            log.info("[BatchedMap] 分批加载完成, 共 {} 个元素, 耗时={}ms cacheKey={}",
                    result.size(), System.currentTimeMillis() - start, cacheKey);
            return result;
        }

        // Redis 无缓存 → 从 DB 加载（首次）
        long start = System.currentTimeMillis();
        Map<Long, Long> ids = dbLoader.get();
        log.info("[BatchedMap] DB 加载完成, 行数={}, 耗时={}ms cacheKey={}",
                ids.size(), System.currentTimeMillis() - start, cacheKey);

        if (!ids.isEmpty()) {
            try {
                cached.putAll(ids);
                cached.expire(1, TimeUnit.HOURS);
                log.info("[BatchedMap] 写入 Redis 完成, 共 {} 个元素, cacheKey={}", ids.size(), cacheKey);
            } catch (Exception e) {
                log.warn("[BatchedMap] 写入 Redis 失败({}), 跳过缓存 cacheKey={}", e.getMessage(), cacheKey);
            }
        }

        return ids;
    }

    /**
     * 新增一批 sourceRowId → globalId 映射到 Redis Map。
     */
    public void addNewIds(String cacheKey, Map<Long, Long> newIds) {
        if (newIds.isEmpty()) return;
        try {
            RMap<Long, Long> cached = redissonClient.getMap(cacheKey);
            cached.putAll(newIds);
        } catch (Exception e) {
            log.warn("[BatchedMap] addNewIds 失败({}), cacheKey={}", e.getMessage(), cacheKey);
        }
    }

    public void clearCache(String cacheKey) {
        redissonClient.getMap(cacheKey).delete();
    }

    public void clearByPattern(String pattern) {
        redissonClient.getKeys().deleteByPattern(pattern);
    }
}
