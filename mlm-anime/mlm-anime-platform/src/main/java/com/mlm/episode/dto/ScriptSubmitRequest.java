package com.mlm.episode.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交剧本请求 DTO。
 */
@Data
@NoArgsConstructor
public class ScriptSubmitRequest {
    /** 项目 ID */
    private Long projectId;
    /** 剧集 ID */
    private Long episodeId;
    /** 剧本内容 */
    private String scriptContent;
}
