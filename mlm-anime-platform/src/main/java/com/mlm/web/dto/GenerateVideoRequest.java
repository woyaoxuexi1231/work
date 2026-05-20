package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenerateVideoRequest {
    private Long projectId;
    private Long episodeId;
    private String imageUrl;
}
