package com.mlm.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 剧集管线状态 — 整数编码
 * <pre>
 * -1 = 失败
 *  0 = (保留)初始
 *  1 = (保留)成功
 *  2 = SCRIPT_DRAFT     剧本创作
 *  3 = SCRIPT_REVIEW    剧本审核
 *  4 = STORYBOARD       拆分镜
 *  5 = GENERATING       AI成片
 *  6 = EPISODE_APPROVAL 终审
 *  7 = COMPLETED        已完成
 * </pre>
 */
public enum EpisodeStatus {
    SCRIPT_DRAFT(2, "剧本创作"),
    SCRIPT_REVIEW(3, "剧本审核"),
    STORYBOARD(4, "拆分镜"),
    GENERATING(5, "AI成片"),
    EPISODE_APPROVAL(6, "终审"),
    COMPLETED(7, "已完成"),
    FAILED(-1, "失败");

    @EnumValue
    private final int code;
    private final String label;

    EpisodeStatus(int code, String label) { this.code = code; this.label = label; }

    @JsonValue
    public int getCode() { return code; }
    public String getLabel() { return label; }

    @JsonCreator
    public static EpisodeStatus of(int code) {
        for (EpisodeStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知剧集状态码: " + code);
    }
}
