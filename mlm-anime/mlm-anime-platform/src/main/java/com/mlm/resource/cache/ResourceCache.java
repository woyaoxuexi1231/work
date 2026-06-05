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
 * 【用途】
 * 缓存已从 MinIO 下载的资源文件字节数组，加速高频资源的重复读取。
 * <p>
 * 【缓存策略】
 * <ul>
 *   <li>容量上限：1000 个条目（超出后按 LRU 淘汰）</li>
 *   <li>过期策略：写入后 3 天自动过期</li>
 *   <li>主动失效：上传新文件时 evict 对应 key，防止前端拿到过期数据</li>
 *   <li>过期监听：通过 Scheduler.systemScheduler() 执行异步过期回调</li>
 * </ul>
 * <p>
 * 【注意事项】
 * 此为本地缓存，多实例部署时各自独立，如需共享缓存请改用 Redis。
 *
 * @author mlm
 * @see com.mlm.resource.service.ResourceService
 */
@Component
public class ResourceCache {

    private static final Logger log = LoggerFactory.getLogger(ResourceCache.class);

    /** 缓存实例（线程安全） */
    private final Cache<String, byte[]> cache;

    /**
     * 构造资源缓存
     * <p>
     * 初始化 Caffeine 缓存，配置容量上限、过期策略和过期回调。
     */
    public ResourceCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofDays(3))
                .scheduler(Scheduler.systemScheduler())
                .removalListener((key, value, cause) ->
                        log.debug("缓存条目过期: key={}, cause={}", key, cause))
                .build();
    }

    /**
     * 根据 ossKey 获取缓存内容
     *
     * @param ossKey MinIO 对象 Key
     * @return 缓存的文件字节数组，未命中时返回 null
     */
    public byte[] get(String ossKey) {
        byte[] data = cache.getIfPresent(ossKey);
        if (data != null) {
            log.debug("缓存命中: key={}", ossKey);
        }
        return data;
    }

    /**
     * 写入缓存
     *
     * @param ossKey MinIO 对象 Key
     * @param data   文件字节数组
     */
    public void put(String ossKey, byte[] data) {
        cache.put(ossKey, data);
        log.debug("缓存写入: key={}, size={}bytes", ossKey, data.length);
    }

    /**
     * 主动清除指定 key 的缓存
     * <p>
     * 上传新文件后调用，避免前端在预签名 URL 刷新前拿到旧数据。
     *
     * @param ossKey MinIO 对象 Key
     */
    public void evict(String ossKey) {
        cache.invalidate(ossKey);
        log.debug("缓存已清除: key={}", ossKey);
    }

    /**
     * 清空全部缓存（运维/调试用）
     */
    public void evictAll() {
        cache.invalidateAll();
        log.info("全部缓存已清除");
    }
}
