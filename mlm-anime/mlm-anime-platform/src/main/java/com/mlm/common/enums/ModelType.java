package com.mlm.common.enums;

/**
 * AI 模型类型枚举 — 定义平台支持的三种 AI 生成模式
 * <p>
 * 每种类型对应不同的厂商适配器实现：
 * <ul>
 *   <li>{@link com.mlm.model.adapter.OpenAiAdapter} — TEXT_TO_TEXT（文生文）</li>
 *   <li>{@link com.mlm.model.adapter.StableDiffusionAdapter} — TEXT_TO_IMAGE（文生图）</li>
 *   <li>{@link com.mlm.model.adapter.KlingAdapter} — IMAGE_TO_VIDEO（图生视频）</li>
 * </ul>
 *
 * @author mlm
 * @see com.mlm.model.core.ModelAdapter 模型适配器接口
 */
public enum ModelType {

    /** 文生文 — 剧本润色、分镜拆分（如 OpenAI GPT） */
    TEXT_TO_TEXT("文生文"),

    /** 文生图 — 场景图片生成（如 Stable Diffusion） */
    TEXT_TO_IMAGE("文生图"),

    /** 图生视频 — 成片视频生成（如可灵 Kling） */
    IMAGE_TO_VIDEO("图生视频");

    /** 中文展示名称 */
    private final String label;

    /**
     * 构造模型类型枚举
     *
     * @param label 中文展示名称
     */
    ModelType(String label) {
        this.label = label;
    }

    /**
     * 获取中文展示名称
     *
     * @return 中文名称
     */
    public String getLabel() {
        return label;
    }
}
