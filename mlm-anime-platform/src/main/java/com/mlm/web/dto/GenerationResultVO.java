package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResultVO {
    private List<GenerationItemVO> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationItemVO {
        private String url;
        private String label;
    }
}
