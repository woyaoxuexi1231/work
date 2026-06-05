package com.riskdatahub.sync.cache;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
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
@Component
@RequiredArgsConstructor
public class SyncCacheHelper {

    private final RedissonClient redissonClient;

    public Set<Long> getExistingIds(String cacheKey, Supplier<Set<Long>> dbLoader) {
        RSet<Long> cached = redissonClient.getSet(cacheKey);
        if (cached.isExists()) {
            return cached.readAll();
        }
        Set<Long> ids = dbLoader.get();
        if (!ids.isEmpty()) {
            cached.addAll(ids);
            cached.expire(1, TimeUnit.HOURS);
        }
        return ids;
    }

    public void addNewIds(String cacheKey, List<Long> newIds) {
        if (newIds.isEmpty()) return;
        RSet<Long> cached = redissonClient.getSet(cacheKey);
        cached.addAll(newIds);
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
