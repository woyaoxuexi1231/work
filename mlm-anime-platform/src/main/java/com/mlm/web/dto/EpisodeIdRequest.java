package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EpisodeIdRequest {
    private Long projectId;
    private Long episodeId;
}
