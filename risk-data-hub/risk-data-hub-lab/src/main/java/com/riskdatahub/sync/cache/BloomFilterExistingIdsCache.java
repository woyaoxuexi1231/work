package com.riskdatahub.sync.cache;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 布隆过滤器缓存策略 — 替代全量 Redis Set，适用于千万级 ID 场景。
 * <p>
 * 将已同步的 sourceRowId 存入 Guava BloomFilter，序列化后缓存到 Redis Bucket。
 * BloomFilter 以 1% 假阳性率为代价，将内存开销从 O(N) 降到固定值（约 12MB/千万级）。
 * </p>
 *
 * <p><b>假阳性影响：</b>如果 BloomFilter 误判某 ID"已存在"，该行会被归入 UPDATE 分支，
 * 后续的 globalId 查询会返回空，UPDATE 实际影响 0 行，不影响数据正确性。</p>
 *
 * <p>启用方式：{@code @Primary} 或 {@code @Qualifier("bloomFilterCache")}</p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sync.cache.strategy", havingValue = "bloom-filter")
public class BloomFilterExistingIdsCache implements ExistingIdsCache {

    /** 预期最大元素数 */
    private static final long EXPECTED_SIZE = 10_000_000L;

    /** 期望的假阳性率 */
    private static final double FPP = 0.01;

    /** Redis key 前缀 */
    private static final String BLOOM_KEY_PREFIX = "sync:bloom:";

    private final RedissonClient redissonClient;

    public BloomFilterExistingIdsCache(RedissonClient redissonClient) {
        log.info("[BloomCache] BloomFilter 缓存初始化完成");
        this.redissonClient = redissonClient;
    }

    /**
     * 获取已存在 ID 集合（返回的 Set 只实现 {@code contains()}，其他操作抛出异常）。
     */
    public Set<Long> getExistingIds(String cacheKey, Supplier<Set<Long>> dbLoader) {
        String bloomKey = BLOOM_KEY_PREFIX + cacheKey;
        RBucket<byte[]> bucket = redissonClient.getBucket(bloomKey);

        // 尝试从 Redis 加载已序列化的 BloomFilter
        byte[] data = bucket.get();
        if (data != null && data.length > 0) {
            try {
                BloomFilter<Long> filter = BloomFilter.readFrom(
                        new ByteArrayInputStream(data), Funnels.longFunnel());
                log.debug("[BloomCache] 命中缓存 bloomKey={}, 元素约 {} 个", bloomKey, filter.approximateElementCount());
                return wrapBloomFilter(filter, bloomKey);
            } catch (Exception e) {
                log.warn("[BloomCache] 反序列化失败({}), 重建缓存 bloomKey={}", e.getMessage(), bloomKey);
            }
        }

        // 缓存未命中，从 DB 加载
        long start = System.currentTimeMillis();
        Set<Long> ids = dbLoader.get();
        log.info("[BloomCache] DB 加载完成, 行数={}, 耗时={}ms bloomKey={}", ids.size(),
                System.currentTimeMillis() - start, bloomKey);

        BloomFilter<Long> filter = BloomFilter.create(Funnels.longFunnel(), EXPECTED_SIZE, FPP);
        for (Long id : ids) {
            filter.put(id);
        }

        // 序列化并写入 Redis
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            filter.writeTo(bos);
            bucket.set(bos.toByteArray());
            bucket.expire(1, TimeUnit.HOURS);
            log.info("[BloomCache] 写入 Redis 完成, bloomKey={}, 大小={}bytes",
                    bloomKey, bos.size());
        } catch (Exception e) {
            log.warn("[BloomCache] 写入 Redis 失败({}), 跳过缓存 bloomKey={}", e.getMessage(), bloomKey);
        }

        return wrapBloomFilter(filter, bloomKey);
    }

    /**
     * 新增 ID 不需要操作 BloomFilter（BloomFilter 不支持删除，但支持追加）。
     * 注意：BloomFilter 的 put() 是幂等的，重复 put 不会影响正确性。
     */
    public void addNewIds(String cacheKey, List<Long> newIds) {
        if (newIds.isEmpty()) return;
        String bloomKey = BLOOM_KEY_PREFIX + cacheKey;
        try {
            RBucket<byte[]> bucket = redissonClient.getBucket(bloomKey);
            byte[] data = bucket.get();
            if (data == null || data.length == 0) return;
            BloomFilter<Long> filter = BloomFilter.readFrom(
                    new ByteArrayInputStream(data), Funnels.longFunnel());
            for (Long id : newIds) {
                filter.put(id);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            filter.writeTo(bos);
            bucket.set(bos.toByteArray());
            bucket.expire(1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[BloomCache] addNewIds 失败({}), bloomKey={}", e.getMessage(), bloomKey);
        }
    }

    public void clearCache(String cacheKey) {
        redissonClient.getBucket(BLOOM_KEY_PREFIX + cacheKey).delete();
    }

    /**
     * 按模式批量删除（scan 匹配 key）。
     */
    public void clearByPattern(String pattern) {
        redissonClient.getKeys().deleteByPattern(BLOOM_KEY_PREFIX + pattern);
    }

    // ==================== 内部：包装 BloomFilter 为 Set 视图 ====================

    private Set<Long> wrapBloomFilter(BloomFilter<Long> filter, String bloomKey) {
        return new AbstractSet<Long>() {
            @Override
            public boolean contains(Object o) {
                if (!(o instanceof Long)) return false;
                return filter.mightContain((Long) o);
            }

            @Override
            public Iterator<Long> iterator() {
                throw new UnsupportedOperationException(
                        "BloomFilter 不支持遍历，bloomKey=" + bloomKey);
            }

            @Override
            public int size() {
                return (int) filter.approximateElementCount();
            }
        };
    }
}
