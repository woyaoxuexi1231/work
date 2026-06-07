package com.riskdatahub.sync.cache;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 已存在 ID 缓存策略接口 — 用于 {@code saveBatch} 中判断 sourceRowId 是否已落库。
 * <p>
 * 当前有 3 种实现，通过 {@code sync.cache.strategy} 配置切换：
 * <ul>
 *   <li>{@code redis-set}（默认）— Redis Set + try-catch 降级</li>
 *   <li>{@code bloom-filter} — Guava BloomFilter 序列化到 Redis</li>
 *   <li>{@code batched-sscan} — Redis Set 分批 SSCAN 加载</li>
 * </ul>
 * </p>
 *
 * @author risk-data-hub
 */
public interface ExistingIdsCache {

    /**
     * 获取已存在的 sourceRowId → globalId 映射。
     *
     * @param cacheKey 缓存 key
     * @param dbLoader 缓存未命中时从 DB 加载的回调
     * @return 已存在的 ID 映射（sourceRowId → globalId）
     */
    Map<Long, Long> getExistingIds(String cacheKey, Supplier<Map<Long, Long>> dbLoader);

    /**
     * 新增一批 sourceRowId → globalId 映射到缓存。
     *
     * @param cacheKey 缓存 key
     * @param newIds   新增的映射（sourceRowId → globalId）
     */
    void addNewIds(String cacheKey, Map<Long, Long> newIds);

    /**
     * 清除指定缓存。
     *
     * @param cacheKey 缓存 key
     */
    void clearCache(String cacheKey);

    /**
     * 按模式批量清除缓存（如 {@code sync:existing:*}）。
     *
     * @param pattern Redis key 模式
     */
    void clearByPattern(String pattern);
}
