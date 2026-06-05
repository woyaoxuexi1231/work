package com.mlm.episode.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 剧集操作请求 DTO（需同时传入项目 ID 和剧集 ID 进行校验）。
 */
@Data
@NoArgsConstructor
public class EpisodeIdRequest {
    /** 项目 ID */
    private Long projectId;
    /** 剧集 ID */
    private Long episodeId;
}
