package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EpisodeAddRequest {
    private Long projectId;
    private String title;
    private Integer episodeNumber;
}
