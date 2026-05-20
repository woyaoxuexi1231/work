package com.mlm.common.enums;

/**
 * Pipeline 剧集状态枚举 — 每集独立走完整个管线
 * <p>
 * 每集的完整生命周期：
 * <pre>
 * SCRIPT_DRAFT → SCRIPT_REVIEW → STORYBOARD → GENERATING → EPISODE_APPROVAL → COMPLETED
 *     ↑              ↓                                                ↓
 *     └── 驳回退回 ──┘                              终审驳回 → GENERATING
 * </pre>
 *
 * @see com.mlm.pipeline.entity.Episode
 */
public enum EpisodeStatus {
    SCRIPT_DRAFT("剧本创作"),
    SCRIPT_REVIEW("剧本审核"),
    STORYBOARD("拆分镜"),
    GENERATING("AI成片"),
    EPISODE_APPROVAL("成片终审"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String label;

    EpisodeStatus(String label) { this.label = label; }

    /** 获取中文展示名称 */
    public String getLabel() { return label; }
}
