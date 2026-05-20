package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成图片请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateImageRequest {
    private Long projectId;
    private Long episodeId;
    private String prompt;
}
