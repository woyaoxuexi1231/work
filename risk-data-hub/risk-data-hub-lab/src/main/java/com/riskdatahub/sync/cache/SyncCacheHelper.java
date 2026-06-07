package com.riskdatahub.sync.cache;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 缓存辅助 — 缓存已同步的 sourceRowId → globalId 映射。
 * <p>
 * 缓存 key 格式：{@code sync:existing:{tableName}:{sourceSystem}}
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sync.cache.strategy", havingValue = "redis-set", matchIfMissing = true)
public class SyncCacheHelper implements ExistingIdsCache {

    private final RedissonClient redissonClient;

    public SyncCacheHelper(RedissonClient redissonClient) {
        log.info("[SyncCache] 使用 Redis 缓存 (Map)");
        this.redissonClient = redissonClient;
    }

    public Map<Long, Long> getExistingIds(String cacheKey, Supplier<Map<Long, Long>> dbLoader) {
        try {
            RMap<Long, Long> cached = redissonClient.getMap(cacheKey);
            if (cached.isExists()) {
                return cached.readAllMap();
            }
        } catch (Exception e) {
            log.error("[SyncCache] Redis 读取失败({}), 降级到 DB 加载 cacheKey={}", e.getMessage(), cacheKey);
        }
        Map<Long, Long> ids = dbLoader.get();
        try {
            if (!ids.isEmpty()) {
                RMap<Long, Long> cached = redissonClient.getMap(cacheKey);
                cached.putAll(ids);
                cached.expire(1, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("[SyncCache] Redis 写入失败({}), 跳过缓存 cacheKey={}", e.getMessage(), cacheKey);
        }
        return ids;
    }

    public void addNewIds(String cacheKey, Map<Long, Long> newIds) {
        if (newIds.isEmpty()) return;
        try {
            RMap<Long, Long> cached = redissonClient.getMap(cacheKey);
            cached.putAll(newIds);
        } catch (Exception e) {
            log.error("[SyncCache] addNewIds 失败({}), cacheKey={}", e.getMessage(), cacheKey);
        }
    }

    public void clearCache(String cacheKey) {
        redissonClient.getMap(cacheKey).delete();
    }

    public void clearByPattern(String pattern) {
        redissonClient.getKeys().deleteByPattern(pattern);
    }
}
