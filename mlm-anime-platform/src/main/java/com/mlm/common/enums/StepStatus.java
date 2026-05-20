package com.mlm.common.enums;

/**
 * 步骤子状态枚举 — 每个 Pipeline 步骤内部的细粒度状态
 * <p>
 * 用于追踪步骤是否为异步执行（如 AI 生成任务）：
 * <pre>
 * PENDING ──→ PROCESSING ──→ SUCCESS
 *                 │
 *                 └──→ FAILED (可手动重试)
 * </pre>
 */
public enum StepStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    SUCCESS("成功"),
    FAILED("失败");

    private final String label;

    StepStatus(String label) { this.label = label; }

    /** 获取中文展示名称 */
    public String getLabel() { return label; }
}
