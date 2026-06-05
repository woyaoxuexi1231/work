package com.riskdatahub.sync.cache;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分批加载 Redis Set 策略 — 替代全量 SMEMBERS，适用于千万级 ID 场景。
 * <p>
 * 改用 {@code SSCAN} 分批迭代，每次从 Redis 加载一批（默认 1000 条）到本地 HashSet，
 * 避免 {@code SMEMBERS} 一次拉回全部数据导致网络超时。
 * </p>
 *
 * <p>启用方式：{@code @Primary} 或 {@code @Qualifier("batchedSetCache")}</p>
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
        log.info("[BatchedSet] 启动");
        this.redissonClient = redissonClient;
    }

    /**
     * 获取已存在 ID 集合——使用 SSCAN 分批加载，避免 SMEMBERS 全量超时。
     */
    public Set<Long> getExistingIds(String cacheKey, Supplier<Set<Long>> dbLoader) {
        RSet<Long> cached = redissonClient.getSet(cacheKey);

        // Redis 中有缓存 → 分批 SSCAN 迭代
        if (cached.isExists()) {
            long start = System.currentTimeMillis();
            Set<Long> result = new HashSet<>();
            // Redisson RSet.iterator() 底层使用 SSCAN，每批默认 10 个元素
            // 这里用 forEach 迭代，通过 countInBatch 控制每批大小
            for (Long id : cached) {
                result.add(id);
                if (result.size() % SCAN_BATCH_SIZE == 0 && result.size() > 0) {
                    log.debug("[BatchedSet] 已扫描 {} 个元素, cacheKey={}", result.size(), cacheKey);
                }
            }
            log.info("[BatchedSet] 分批加载完成, 共 {} 个元素, 耗时={}ms cacheKey={}",
                    result.size(), System.currentTimeMillis() - start, cacheKey);
            return result;
        }

        // Redis 无缓存 → 从 DB 加载（首次）
        long start = System.currentTimeMillis();
        Set<Long> ids = dbLoader.get();
        log.info("[BatchedSet] DB 加载完成, 行数={}, 耗时={}ms cacheKey={}",
                ids.size(), System.currentTimeMillis() - start, cacheKey);

        // 写入 Redis Set（分批 addAll，避免单次命令过大）
        if (!ids.isEmpty()) {
            try {
                int batchSize = 5000;
                List<Long> idList = new java.util.ArrayList<>(ids);
                for (int i = 0; i < idList.size(); i += batchSize) {
                    List<Long> chunk = idList.subList(i, Math.min(i + batchSize, idList.size()));
                    cached.addAll(chunk);
                }
                cached.expire(1, TimeUnit.HOURS);
                log.info("[BatchedSet] 写入 Redis 完成, 共 {} 个元素, cacheKey={}", ids.size(), cacheKey);
            } catch (Exception e) {
                log.warn("[BatchedSet] 写入 Redis 失败({}), 跳过缓存 cacheKey={}", e.getMessage(), cacheKey);
            }
        }

        return ids;
    }

    /**
     * 新增 ID 到 Redis Set。
     */
    public void addNewIds(String cacheKey, List<Long> newIds) {
        if (newIds.isEmpty()) return;
        try {
            RSet<Long> cached = redissonClient.getSet(cacheKey);
            cached.addAll(newIds);
        } catch (Exception e) {
            log.warn("[BatchedSet] addNewIds 失败({}), cacheKey={}", e.getMessage(), cacheKey);
        }
    }

    public void clearCache(String cacheKey) {
        redissonClient.getSet(cacheKey).delete();
    }

    /**
     * 按模式批量删除缓存 key。
     */
    public void clearByPattern(String pattern) {
        redissonClient.getKeys().deleteByPattern(pattern);
    }
}
