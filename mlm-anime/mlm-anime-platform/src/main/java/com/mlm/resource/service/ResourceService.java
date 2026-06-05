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
 * 资源服务 — 统一管理上传、下载和查询
 * <p>
 * 【上传流程】
 * <ol>
 *   <li>上传文件到 MinIO 获取 ossKey</li>
 *   <li>生成预签名 URL（7 天有效）</li>
 *   <li>保存资源元信息到数据库</li>
 *   <li>清除该 key 的本地缓存（防止旧缓存被新文件影响）</li>
 * </ol>
 * <p>
 * 【下载流程】
 * 查本地缓存（Caffeine TTL=3 天）→ 未命中则从 MinIO 拉取 → 写入缓存
 *
 * @author mlm
 * @see Resource 资源实体
 * @see OssService MinIO 对象存储服务
 * @see ResourceCache 资源本地缓存
 */
@Service
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceMapper resourceMapper;
    private final OssService ossService;
    private final ResourceCache cache;

    /**
     * 构造资源服务
     *
     * @param resourceMapper 资源 Mapper
     * @param ossService     MinIO 对象存储服务
     * @param cache          资源本地缓存
     */
    public ResourceService(ResourceMapper resourceMapper,
                           OssService ossService,
                           ResourceCache cache) {
        this.resourceMapper = resourceMapper;
        this.ossService = ossService;
        this.cache = cache;
    }

    /**
     * 上传文件并入库
     * <p>
     * 【执行流程】
     * <ol>
     *   <li>上传到 MinIO 获取 ossKey</li>
     *   <li>生成预签名 URL（7 天有效）</li>
     *   <li>保存资源元信息到 DB</li>
     *   <li>清除该 key 的本地缓存</li>
     * </ol>
     *
     * @param file 待上传的文件
     * @param name 资源名称
     * @param type 资源类型
     * @return 入库后的资源实体（含自增 ID）
     */
    @Transactional(rollbackFor = Exception.class)
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

        // 清除旧缓存，避免前端拿到过期数据
        cache.evict(ossKey);

        log.info("资源上传成功: id={}, name={}, type={}, size={}, ossKey={}",
                resource.getId(), name, type, file.getSize(), ossKey);
        return resource;
    }

    /**
     * 下载文件（带本地缓存）
     * <p>
     * 优先从 Caffeine 本地缓存读取，未命中则从 MinIO 拉取并写入缓存。
     * 缓存 TTL = 3 天。
     *
     * @param ossKey MinIO 存储 Key
     * @return 文件字节数组
     * @throws RuntimeException 下载失败时抛出
     */
    public byte[] download(String ossKey) {
        // 尝试从缓存获取
        byte[] cached = cache.get(ossKey);
        if (cached != null) {
            log.debug("资源缓存命中: ossKey={}", ossKey);
            return cached;
        }

        // 缓存未命中，从 MinIO 拉取
        try (InputStream inputStream = ossService.download(ossKey);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            byte[] data = outputStream.toByteArray();
            cache.put(ossKey, data);
            log.debug("资源缓存写入: ossKey={}, size={}bytes", ossKey, data.length);
            return data;
        } catch (Exception e) {
            log.error("资源下载失败: ossKey={}", ossKey, e);
            throw new RuntimeException("下载资源失败", e);
        }
    }

    /**
     * 查询所有资源列表
     *
     * @return 资源列表
     */
    public List<Resource> listAll() {
        return resourceMapper.selectList(null);
    }

    /**
     * 根据主键查询资源
     *
     * @param id 资源 ID
     * @return 资源实体，不存在时返回 null
     */
    public Resource getById(Long id) {
        return resourceMapper.selectById(id);
    }
}
