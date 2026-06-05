package com.mlm.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询生成结果请求 DTO。
 */
@Data
@NoArgsConstructor
public class ResultsRequest {
    /** 剧集 ID */
    private Long episodeId;
}
