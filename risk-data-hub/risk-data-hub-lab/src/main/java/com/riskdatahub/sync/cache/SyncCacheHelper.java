package com.riskdatahub.sync.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Redis 缓存辅助 — 缓存已同步的 source_row_id，避免每次同步都查库判断存在性。
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
        log.info("[SyncCache] 使用 Redis 缓存");
        this.redissonClient = redissonClient;
    }

    public Set<Long> getExistingIds(String cacheKey, Supplier<Set<Long>> dbLoader) {
        try {
            RSet<Long> cached = redissonClient.getSet(cacheKey);
            if (cached.isExists()) {
                return cached.readAll();
            }
        } catch (Exception e) {
            log.error("[SyncCache] Redis 读取失败({}), 降级到 DB 加载 cacheKey={}", e.getMessage(), cacheKey);
        }
        // Redis 不可用时降级到 DB 加载
        Set<Long> ids = dbLoader.get();
        try {
            if (!ids.isEmpty()) {
                RSet<Long> cached = redissonClient.getSet(cacheKey);
                cached.addAll(ids);
                cached.expire(1, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("[SyncCache] Redis 写入失败({}), 跳过缓存 cacheKey={}", e.getMessage(), cacheKey);
        }
        return ids;
    }

    public void addNewIds(String cacheKey, List<Long> newIds) {
        if (newIds.isEmpty()) return;
        try {
            RSet<Long> cached = redissonClient.getSet(cacheKey);
            cached.addAll(newIds);
        } catch (Exception e) {
            log.error("[SyncCache] addNewIds 失败({}), cacheKey={}", e.getMessage(), cacheKey);
        }
    }

    public void clearCache(String cacheKey) {
        redissonClient.getSet(cacheKey).delete();
    }

    /**
     * 按模式批量删除缓存 key（如 {@code sync:existing:*}）。
     *
     * @param pattern Redis key 模式，支持 {@code *} 通配符
     */
    public void clearByPattern(String pattern) {
        redissonClient.getKeys().deleteByPattern(pattern);
    }
}
