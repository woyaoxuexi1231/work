package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成结果请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultsRequest {
    private Long episodeId;
}
