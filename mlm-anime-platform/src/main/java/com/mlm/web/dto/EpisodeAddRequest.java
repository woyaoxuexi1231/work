package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加剧集请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeAddRequest {
    private Long projectId;
    private String title;
    private Integer episodeNumber;
}
