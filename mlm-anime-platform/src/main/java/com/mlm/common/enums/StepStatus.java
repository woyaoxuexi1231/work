package com.mlm.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 步骤子状态 — 整数编码
 * <pre>
 * -1 = FAILED    失败
 *  0 = PENDING   待处理
 *  1 = SUCCESS   成功
 *  2 = PROCESSING 处理中
 * </pre>
 */
@Getter
public enum StepStatus {
    PENDING(0, "待处理"),
    SUCCESS(1, "成功"),
    FAILED(-1, "失败"),
    PROCESSING(2, "处理中");

    @EnumValue
    private final int code;
    private final String label;

    StepStatus(int code, String label) { this.code = code; this.label = label; }

    public static StepStatus of(int code) {
        for (StepStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知步骤状态码: " + code);
    }
}
