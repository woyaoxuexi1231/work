package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文生图请求 DTO
 *
 * @author mlm
 */
@Data
@NoArgsConstructor
public class GenerateImageRequest {
    /** 项目 ID */
    private Long projectId;
    /** 剧集 ID */
    private Long episodeId;
    /** 正向提示词 */
    private String prompt;
}
