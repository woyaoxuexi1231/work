package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 剧集ID请求（仅含projectId和episodeId）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeIdRequest {
    private Long projectId;
    private Long episodeId;
}
