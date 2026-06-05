package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加剧集请求 DTO
 *
 * @author mlm
 */
@Data
@NoArgsConstructor
public class EpisodeAddRequest {
    /** 所属项目 ID */
    private Long projectId;
    /** 剧集标题 */
    private String title;
    /** 集号（不传则默认为 1） */
    private Integer episodeNumber;
}
