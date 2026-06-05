package com.mlm.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 剧集管线状态枚举 — 整数编码
 * <p>
 * 定义剧集在 Pipeline 中的全生命周期状态，使用整型编码存储于数据库。
 * 枚举通过 MyBatis-Plus 的 {@link EnumValue} 注解实现与数据库 int 字段的映射。
 * <p>
 * 【状态编码表】
 * <pre>
 * -1 = FAILED            失败
 *  2 = SCRIPT_DRAFT      剧本创作
 *  3 = SCRIPT_REVIEW     剧本审核
 *  4 = STORYBOARD        拆分镜
 *  5 = GENERATING        AI成片
 *  6 = EPISODE_APPROVAL  终审
 *  7 = COMPLETED         已完成
 * </pre>
 * <p>
 * 【流转规则】
 * 主线：2 → 3 → 4 → 5 → 6 → 7
 * 驳回：3 → 2, 6 → 5
 *
 * @author mlm
 * @see com.mlm.pipeline.engine.StateMachine
 * @see com.mlm.pipeline.engine.PipelineEngine
 */
@Getter
public enum EpisodeStatus {

    /** 剧本创作（2）— 用户撰写剧本初稿 */
    SCRIPT_DRAFT(2, "剧本创作"),

    /** 剧本审核（3）— 审核人员审阅剧本 */
    SCRIPT_REVIEW(3, "剧本审核"),

    /** 拆分镜（4）— AI 自动将剧本拆分为分镜脚本 */
    STORYBOARD(4, "拆分镜"),

    /** AI 成片（5）— 用户手动触发图片和视频生成 */
    GENERATING(5, "AI成片"),

    /** 终审（6）— 审核人员审阅 AI 成片 */
    EPISODE_APPROVAL(6, "终审"),

    /** 已完成（7）— 剧集全部流程走完 */
    COMPLETED(7, "已完成"),

    /** 失败（-1）— 步骤执行异常 */
    FAILED(-1, "失败");

    /** 数据库存储的整型编码 */
    @EnumValue
    private final int code;

    /** 中文展示名称 */
    private final String label;

    /**
     * 构造剧集状态枚举
     *
     * @param code  整型编码
     * @param label 中文名称
     */
    EpisodeStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 根据整型编码获取对应的枚举实例
     *
     * @param code 整型编码
     * @return 对应的枚举实例
     * @throws IllegalArgumentException 编码不匹配时抛出
     */
    public static EpisodeStatus of(int code) {
        for (EpisodeStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知剧集状态码: " + code);
    }
}
