package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ScriptSubmitRequest {
    private Long projectId;
    private Long episodeId;
    private String scriptContent;
}
