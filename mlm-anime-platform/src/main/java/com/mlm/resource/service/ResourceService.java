package com.mlm.resource.service;

import com.mlm.common.enums.ResourceType;
import com.mlm.resource.cache.ResourceCache;
import com.mlm.resource.entity.Resource;
import com.mlm.resource.mapper.ResourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * 资源服务 — 统一管理上传/下载/查询
 * <p>
 * 上传流程：MinIO 存储 → 预签名 URL → 保存元信息到 DB → 清除缓存
 * 下载流程：查本地缓存（Caffeine TTL=3天）→ 未命中则从 MinIO 拉取 → 写入缓存
 */
@Service
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceMapper resourceMapper;
    private final OssService ossService;
    private final ResourceCache cache;

    public ResourceService(ResourceMapper resourceMapper, OssService ossService, ResourceCache cache) {
        this.resourceMapper = resourceMapper;
        this.ossService = ossService;
        this.cache = cache;
    }

    /**
     * 上传文件并入库
     * <p>
     * 1. 上传到 MinIO 获取 ossKey
     * 2. 生成预签名 URL（7天有效）
     * 3. 保存资源元信息到 DB
     * 4. 清除该 key 的本地缓存
     *
     * @param file 上传的文件
     * @param name 资源名称
     * @param type 资源类型
     * @return 入库后的资源实体（含自增 id）
     */
    @Transactional
    public Resource upload(MultipartFile file, String name, ResourceType type) {
        String ossKey = ossService.upload(file);
        String ossUrl = ossService.getPresignedUrl(ossKey);

        Resource resource = new Resource();
        resource.setName(name);
        resource.setType(type);
        resource.setOssKey(ossKey);
        resource.setOssUrl(ossUrl);
        resource.setFileSize(file.getSize());
        resourceMapper.insert(resource);

        // 上传新文件后清除旧缓存，避免前端拿到 stale 数据
        cache.evict(ossKey);

        log.info("资源入库: id={}, name={}, type={}, size={}", resource.getId(), name, type, file.getSize());
        return resource;
    }

    /**
     * 下载文件（带缓存）
     * <p>
     * 优先从 Caffeine 本地缓存读取，未命中则从 MinIO 拉取并写入缓存。
     * 缓存 TTL = 3 天。
     *
     * @param ossKey MinIO 存储 Key
     * @return 文件字节数组
     */
    public byte[] download(String ossKey) {
        byte[] cached = cache.get(ossKey);
        if (cached != null) {
            log.debug("资源缓存命中: ossKey={}", ossKey);
            return cached;
        }

        try (InputStream is = ossService.download(ossKey);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            byte[] data = bos.toByteArray();
            cache.put(ossKey, data);
            log.debug("资源缓存写入: ossKey={}, size={}bytes", ossKey, data.length);
            return data;
        } catch (Exception e) {
            log.error("下载失败: ossKey={}", ossKey, e);
            throw new RuntimeException("下载资源失败", e);
        }
    }

    /** 查询所有资源 */
    public List<Resource> listAll() {
        return resourceMapper.selectList(null);
    }

    /** 根据主键查询 */
    public Resource getById(Long id) {
        return resourceMapper.selectById(id);
    }
}
