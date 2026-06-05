package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 生成结果 VO — 剧集中所有 AI 生成产物的列表
 *
 * @author mlm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResultVO {
    /** 生成产物列表 */
    private List<GenerationItemVO> items;

    /**
     * 单个生成产物
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationItemVO {
        /** 产物访问 URL */
        private String url;
        /** 产物展示标签 */
        private String label;
    }
}
