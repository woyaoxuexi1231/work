package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenerateImageRequest {
    private Long projectId;
    private Long episodeId;
    private String prompt;
}
