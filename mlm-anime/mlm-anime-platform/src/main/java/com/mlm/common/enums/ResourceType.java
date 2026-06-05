package com.mlm.common.enums;

/**
 * 资源类型枚举 — 标识上传到 MinIO 的文件类型
 * <p>
 * 用于区分不同类型的资源文件，便于前端按类型筛选展示。
 *
 * @author mlm
 * @see com.mlm.resource.entity.Resource
 */
public enum ResourceType {
    /** 图片文件（如 PNG、JPG） */
    IMAGE,

    /** 视频文件（如 MP4） */
    VIDEO,

    /** 音频文件（如 MP3、WAV） */
    AUDIO,

    /** 文本文件（如 TXT、剧本） */
    TEXT
}
