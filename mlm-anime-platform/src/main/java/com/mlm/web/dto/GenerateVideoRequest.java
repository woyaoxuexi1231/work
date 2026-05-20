package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成视频请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateVideoRequest {
    private Long projectId;
    private Long episodeId;
    private String imageUrl;
}
