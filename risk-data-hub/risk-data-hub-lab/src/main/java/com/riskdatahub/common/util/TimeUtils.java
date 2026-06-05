package com.riskdatahub.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具类 — 提供统一的日期时间格式化能力。
 * <p>
 * 项目中所有 "yyyy-MM-dd HH:mm:ss" 格式的时间字符串必须通过此类生成，
 * 避免各 Service/Template 重复定义 {@link DateTimeFormatter}。
 * </p>
 *
 * @author risk-data-hub
 */
public final class TimeUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtils() {
    }

    /**
     * 获取当前时间的格式化字符串。
     *
     * @return 当前时间的 "yyyy-MM-dd HH:mm:ss" 格式字符串
     */
    public static String now() {
        return LocalDateTime.now().format(FORMATTER);
    }
}
