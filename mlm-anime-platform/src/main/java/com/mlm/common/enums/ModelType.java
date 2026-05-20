package com.mlm.common.enums;

/**
 * AI 模型类型枚举
 * <p>
 * 定义平台支持的三种 AI 生成模式。
 * 每种类型对应不同的厂商适配器实现。
 *
 * @see com.mlm.model.adapter.OpenAiAdapter       — TEXT_TO_TEXT
 * @see com.mlm.model.adapter.StableDiffusionAdapter — TEXT_TO_IMAGE
 * @see com.mlm.model.adapter.KlingAdapter          — IMAGE_TO_VIDEO
 */
public enum ModelType {
    TEXT_TO_TEXT("文生文"),
    TEXT_TO_IMAGE("文生图"),
    IMAGE_TO_VIDEO("图生视频");

    private final String label;

    ModelType(String label) { this.label = label; }

    /** 获取中文展示名称 */
    public String getLabel() { return label; }
}
