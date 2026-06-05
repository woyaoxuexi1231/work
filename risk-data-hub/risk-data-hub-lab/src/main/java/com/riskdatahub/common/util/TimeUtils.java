package com.riskdatahub.common.util;

import java.time.LocalDateTime;

/**
 * 时间工具类 — 提供统一的日期时间获取能力。
 * <p>
 * 项目中所有当前时间必须通过此方法获取，避免各 Service/Template 重复定义。
 * </p>
 *
 * @author risk-data-hub
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    /**
     * 获取当前时间。
     *
     * @return 当前时间
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
