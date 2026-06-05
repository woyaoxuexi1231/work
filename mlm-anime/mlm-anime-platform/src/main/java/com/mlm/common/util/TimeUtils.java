package com.mlm.common.util;

import java.time.LocalDateTime;

/**
 * 时间工具类 — 提供统一的日期时间获取能力。
 * <p>
 * 项目中所有当前时间通过此方法获取，避免各 Service 重复定义 {@code LocalDateTime.now()}。
 * </p>
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
