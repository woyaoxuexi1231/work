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
 * MinIO 对象存储服务 — 文件的上传/下载/删除/预签名
 * <p>
 * 所有文件通过此服务写入 MinIO，数据库仅存 ossKey 引用。
 * 预签名 URL 用于前端直连预览（避免后端代理流量），有效期 7 天。
 */
@Service
public class OssService {

    private static final Logger log = LoggerFactory.getLogger(OssService.class);

    private final MinioClient minioClient;
    private final String bucket;

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

    /** 启动时检查桶是否存在，不存在则自动创建 */
    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("创建MinIO桶: {}", bucket);
            }
        } catch (Exception e) {
            log.error("初始化MinIO桶失败", e);
        }
    }

    /**
     * 上传文件，返回 OSS Key
     * <p>
     * Key 格式：UUID_原始文件名，避免同名覆盖。
     *
     * @param file 上传的 Multipart 文件
     * @return MinIO 中的对象 Key
     */
    public String upload(MultipartFile file) {
        String key = UUID.randomUUID() + "_" + file.getOriginalFilename();
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .stream(is, file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
            log.info("上传成功: key={}, size={}", key, file.getSize());
            return key;
        } catch (Exception e) {
            log.error("上传失败: key={}", key, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 获取预签名 GET URL（有效期 7 天）
     * <p>
     * 前端直接通过此 URL 预览/下载，不走后端带宽，降低服务器压力。
     */
    public String getPresignedUrl(String key) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .method(Method.GET)
                    .expiry(7, TimeUnit.DAYS)
                    .build()
            );
        } catch (Exception e) {
            log.error("获取预签名URL失败: key={}", key, e);
            return null;
        }
    }

    /** 下载文件流 */
    public InputStream download(String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());
        } catch (Exception e) {
            log.error("下载失败: key={}", key, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /** 删除文件 */
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());
            log.info("删除成功: key={}", key);
        } catch (Exception e) {
            log.error("删除失败: key={}", key, e);
        }
    }
}
