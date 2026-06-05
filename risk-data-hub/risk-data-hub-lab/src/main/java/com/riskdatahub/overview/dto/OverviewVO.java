package com.riskdatahub.overview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 系统概览视图对象 — 提供给前端总览页面展示的数据。
 *
 * @author risk-data-hub
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverviewVO {

    /** 项目名称 */
    private String project;

    /** 项目摘要 */
    private String summary;

    /** 系统拓扑（上游系统 → 中台库） */
    private Map<String, List<String>> topology;

    /** 各业务系统的表统计 */
    private Map<String, Object> businessTableStats;

    /** 中台表统计 */
    private Map<String, Integer> hubTableStats;

    /** 数据源数量 */
    private int datasourceCount;

    /** 清洗交易记录数 */
    private int cleanTradeCount;

    /** 事件消息数 */
    private int eventCount;

    /** 架构设计说明 */
    private List<String> architectureAnswers;

    /** Leaf 发号器状态 */
    private LeafStateVO leafState;

    /**
     * Leaf 发号器状态视图对象。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeafStateVO {
        /** 当前号段起始值 */
        private Long currentStart;
        /** 当前号段下一个要分配的 ID */
        private Long currentNext;
        /** 号段步长描述 */
        private String step;
        /** 发号器模式 */
        private String mode;
        /** 状态描述 */
        private String description;
    }
}
