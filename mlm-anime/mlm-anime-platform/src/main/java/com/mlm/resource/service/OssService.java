package com.mlm.resource.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储服务 — 文件的上传、下载、删除和预签名 URL 生成
 * <p>
 * 【职责】
 * <ul>
 *   <li>上传文件到 MinIO 桶（自动生成唯一 Key）</li>
 *   <li>生成预签名 GET URL（7 天有效期，前端直连预览）</li>
 *   <li>下载文件流</li>
 *   <li>删除文件</li>
 * </ul>
 * <p>
 * 【设计决策】
 * <ul>
 *   <li>预签名 URL 用于前端直连预览/下载，避免后端代理流量，降低服务器带宽压力</li>
 *   <li>文件 Key 格式为 {@code UUID_原始文件名}，避免同名覆盖</li>
 *   <li>启动时自动检查并创建桶（ensureBucket）</li>
 *   <li>数据库仅存 ossKey 引用，不存文件二进制数据</li>
 * </ul>
 *
 * @author mlm
 * @see ResourceService 资源服务
 */
@Service
public class OssService {

    private static final Logger log = LoggerFactory.getLogger(OssService.class);

    /** 预签名 URL 有效期：7 天 */
    private static final int PRESIGNED_URL_EXPIRY_DAYS = 7;

    private final MinioClient minioClient;
    private final String bucket;

    /**
     * 构造 MinIO 对象存储服务
     * <p>
     * 构造时自动调用 {@link #ensureBucket()} 确保目标桶存在。
     *
     * @param endpoint  MinIO 服务端点
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     * @param bucket    默认桶名称
     */
    public OssService(@Value("${minio.endpoint}") String endpoint,
                      @Value("${minio.access-key}") String accessKey,
                      @Value("${minio.secret-key}") String secretKey,
                      @Value("${minio.bucket}") String bucket) {
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket();
    }

    /**
     * 启动时检查桶是否存在，不存在则自动创建
     */
    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 桶已创建: bucket={}", bucket);
            } else {
                log.debug("MinIO 桶已存在: bucket={}", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO 桶初始化失败: bucket={}", bucket, e);
        }
    }

    /**
     * 上传文件到 MinIO
     * <p>
     * Key 格式为 {@code UUID_原始文件名}，确保全局唯一避免同名覆盖。
     *
     * @param file 待上传的 Multipart 文件
     * @return MinIO 中的对象 Key
     * @throws RuntimeException 上传失败时抛出
     */
    public String upload(MultipartFile file) {
        String key = UUID.randomUUID() + "_" + file.getOriginalFilename();

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("文件上传成功: key={}, size={}, contentType={}",
                    key, file.getSize(), file.getContentType());
            return key;
        } catch (Exception e) {
            log.error("文件上传失败: key={}", key, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 获取预签名 GET URL（有效期 7 天）
     * <p>
     * 前端直接通过此 URL 预览/下载文件，不走后端带宽，降低服务器压力。
     *
     * @param key MinIO 对象 Key
     * @return 预签名 URL，获取失败时返回 null
     */
    public String getPresignedUrl(String key) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .method(Method.GET)
                            .expiry(PRESIGNED_URL_EXPIRY_DAYS, TimeUnit.DAYS)
                            .build());
            log.debug("预签名 URL 生成成功: key={}", key);
            return url;
        } catch (Exception e) {
            log.error("预签名 URL 生成失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 下载文件流
     *
     * @param key MinIO 对象 Key
     * @return 文件输入流
     * @throws RuntimeException 下载失败时抛出
     */
    public InputStream download(String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            log.error("文件下载失败: key={}", key, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 删除文件
     *
     * @param key MinIO 对象 Key
     */
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            log.info("文件删除成功: key={}", key);
        } catch (Exception e) {
            log.error("文件删除失败: key={}", key, e);
        }
    }
}
