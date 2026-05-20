package com.mlm.common.enums;

/**
 * Pipeline 项目主状态枚举
 * <p>
 * 定义项目在 Pipeline 中的全生命周期状态。
 * 流转规则见 {@link com.mlm.pipeline.engine.StateMachine}。
 * <pre>
 * DRAFT → REVIEW → STORYBOARD → GENERATING → APPROVAL → COMPLETED
 *   ↑        ↓                                          ↓
 *   └── 驳回 ┘                             终审驳回 → GENERATING
 * </pre>
 */
public enum ProjectStatus {
    DRAFT("剧本创作"),
    REVIEW("审核中"),
    STORYBOARD("拆分镜"),
    GENERATING("AI成片"),
    APPROVAL("终审"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String label;

    ProjectStatus(String label) { this.label = label; }

    /** 获取中文展示名称 */
    public String getLabel() { return label; }
}
