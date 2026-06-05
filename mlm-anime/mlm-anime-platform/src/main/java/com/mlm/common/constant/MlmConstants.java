package com.mlm.common.constant;

/**
 * 平台常量定义 — 集中管理所有 AI 厂商标识和业务类型常量。
 * <p>
 * 所有引用 AI 厂商或审核类型的地方必须通过此类引用，
 * 禁止在业务代码中硬编码字符串常量。
 * </p>
 */
public final class MlmConstants {

    private MlmConstants() {
    }

    // ==================== AI 厂商标识 ====================

    /** OpenAI — 文生文模型（剧本润色、分镜拆分） */
    public static final String VENDOR_OPENAI = "openai";

    /** Stable Diffusion — 文生图模型（场景生成） */
    public static final String VENDOR_SD = "stable_diffusion";

    /** 可灵 Kling — 图生视频模型（成片合成） */
    public static final String VENDOR_KLING = "kling";

    // ==================== 审核消息类型 ====================

    /** 审核消息类型：剧本审核 */
    public static final String REVIEW_TYPE_SCRIPT = "SCRIPT_REVIEW";

    /** 审核消息类型：成片终审 */
    public static final String REVIEW_TYPE_EPISODE = "EPISODE_REVIEW";
}
