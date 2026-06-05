package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询生成结果请求 DTO
 *
 * @author mlm
 */
@Data
@NoArgsConstructor
public class ResultsRequest {
    /** 剧集 ID */
    private Long episodeId;
}
