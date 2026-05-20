package com.example.dynamicds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 中台总览响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverviewVO {
    private String project;
    private String summary;
    private List<String> topology;
    private Map<String, Integer> businessTableStats;
    private Map<String, Integer> hubTableStats;
    private int datasourceCount;
    private int cleanTradeCount;
    private int eventCount;
    private List<String> architectureAnswers;
    private LeafStateVO leafState;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeafStateVO {
        private Long currentStart;
        private Long currentNext;
        private String step;
        private String mode;
        private String description;
    }
}
