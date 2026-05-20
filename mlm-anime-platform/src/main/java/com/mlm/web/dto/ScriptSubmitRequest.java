package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交剧本请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptSubmitRequest {
    private Long projectId;
    private Long episodeId;
    private String scriptContent;
}
