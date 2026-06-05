package com.mlm.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图生视频请求 DTO。
 */
@Data
@NoArgsConstructor
public class GenerateVideoRequest {
    /** 项目 ID */
    private Long projectId;
    /** 剧集 ID */
    private Long episodeId;
    /** 参考图片 URL */
    private String imageUrl;
}
