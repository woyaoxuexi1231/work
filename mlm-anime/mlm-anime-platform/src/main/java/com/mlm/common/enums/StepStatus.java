package com.mlm.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 步骤子状态枚举 — 整数编码
 * <p>
 * 定义 Pipeline 中各步骤的内部执行状态，与主状态（{@link EpisodeStatus}）配合使用。
 * 枚举通过 MyBatis-Plus 的 {@link EnumValue} 注解实现与数据库 int 字段的映射。
 * <p>
 * 【状态编码表】
 * <pre>
 * -1 = FAILED     失败
 *  0 = PENDING    待处理（等待人工操作或 AI 结果）
 *  1 = SUCCESS    成功
 *  2 = PROCESSING 处理中（AI 任务进行中）
 * </pre>
 *
 * @author mlm
 * @see EpisodeStatus 主状态枚举
 */
@Getter
public enum StepStatus {

    /** 待处理（0）— 等待人工操作或 AI 结果返回 */
    PENDING(0, "待处理"),

    /** 成功（1）— 步骤执行成功 */
    SUCCESS(1, "成功"),

    /** 失败（-1）— 步骤执行异常 */
    FAILED(-1, "失败"),

    /** 处理中（2）— AI 任务提交成功，等待厂商返回 */
    PROCESSING(2, "处理中");

    /** 数据库存储的整型编码 */
    @EnumValue
    private final int code;

    /** 中文展示名称 */
    private final String label;

    /**
     * 构造步骤子状态枚举
     *
     * @param code  整型编码
     * @param label 中文名称
     */
    StepStatus(int code, String label) {
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
    public static StepStatus of(int code) {
        for (StepStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知步骤状态码: " + code);
    }
}
