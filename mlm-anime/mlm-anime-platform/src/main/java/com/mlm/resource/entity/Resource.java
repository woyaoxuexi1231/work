package com.mlm.resource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mlm.common.enums.ResourceType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资源实体 — 对应数据库 resource 表
 * <p>
 * 存储上传到 MinIO 的文件元信息（图片/视频/音频/文本），
 * 实际文件内容通过 {@code ossKey} 从 MinIO 读取。
 * <p>
 * 【数据流】
 * <ol>
 *   <li>用户上传文件</li>
 *   <li>{@link com.mlm.resource.service.OssService} 写入 MinIO 并返回 ossKey</li>
 *   <li>生成预签名 URL（7 天有效，前端直连访问）</li>
 *   <li>资源元信息入库</li>
 *   <li>高频读取通过 {@link com.mlm.resource.cache.ResourceCache} 本地缓存加速</li>
 * </ol>
 *
 * @author mlm
 * @see com.mlm.resource.service.ResourceService
 * @see com.mlm.resource.cache.ResourceCache
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("resource")
public class Resource {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资源名称 */
    private String name;

    /** 资源类型：IMAGE / VIDEO / AUDIO / TEXT */
    private ResourceType type;

    /** MinIO 存储 Key */
    private String ossKey;

    /** 预签名访问 URL（7 天有效） */
    private String ossUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
