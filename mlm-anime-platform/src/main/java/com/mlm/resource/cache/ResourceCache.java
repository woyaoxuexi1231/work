package com.mlm.resource.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 资源本地缓存 — Caffeine 实现
 * <p>
 * 缓存资源文件的字节数组，用于加速高频资源的重复读取。
 * <ul>
 *   <li>容量上限：1000 个条目（LRU 淘汰）</li>
 *   <li>过期策略：写入后 3 天自动过期</li>
 *   <li>主动失效：上传新文件时 evict 对应 key</li>
 * </ul>
 * 注意：此为本地缓存，多实例部署时各自独立，如需共享请改用 Redis。
 */
@Component
public class ResourceCache {

    private static final Logger log = LoggerFactory.getLogger(ResourceCache.class);

    private final Cache<String, byte[]> cache;

    public ResourceCache() {
        this.cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofDays(3))
            .scheduler(Scheduler.systemScheduler())
            .removalListener((key, value, cause) ->
                log.debug("缓存过期: key={}, cause={}", key, cause))
            .build();
    }

    /** 根据 ossKey 获取缓存，未命中返回 null */
    public byte[] get(String ossKey) {
        byte[] data = cache.getIfPresent(ossKey);
        if (data != null) {
            log.debug("缓存命中: key={}", ossKey);
        }
        return data;
    }

    /** 写入缓存 */
    public void put(String ossKey, byte[] data) {
        cache.put(ossKey, data);
        log.debug("缓存写入: key={}, size={}bytes", ossKey, data.length);
    }

    /** 上传新文件时主动清除缓存，避免前端拿到过时数据 */
    public void evict(String ossKey) {
        cache.invalidate(ossKey);
        log.debug("缓存清除: key={}", ossKey);
    }

    /** 清空全部缓存（运维/调试用） */
    public void evictAll() {
        cache.invalidateAll();
    }
}
