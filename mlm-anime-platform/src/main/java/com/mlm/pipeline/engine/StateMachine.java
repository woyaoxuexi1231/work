package com.mlm.pipeline.engine;

import com.mlm.common.enums.EpisodeStatus;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline 状态机 — 定义剧集状态的合法流转路径（每集独立）
 * <p>
 * 主线流转（自动推进）：
 * <pre>
 * SCRIPT_DRAFT → SCRIPT_REVIEW → STORYBOARD → GENERATING → EPISODE_APPROVAL → COMPLETED
 * </pre>
 * 驳回路径（人工操作）：
 * <pre>
 * SCRIPT_REVIEW → SCRIPT_DRAFT    （剧本驳回，退回重写）
 * EPISODE_APPROVAL → GENERATING   （成片终审驳回，退回重做）
 * </pre>
 */
public class StateMachine {

    /** 正常流转: 当前状态 → 下一个状态 */
    private static final Map<EpisodeStatus, EpisodeStatus> TRANSITIONS = Map.of(
        EpisodeStatus.SCRIPT_DRAFT,      EpisodeStatus.SCRIPT_REVIEW,
        EpisodeStatus.SCRIPT_REVIEW,     EpisodeStatus.STORYBOARD,
        EpisodeStatus.STORYBOARD,        EpisodeStatus.GENERATING,
        EpisodeStatus.GENERATING,        EpisodeStatus.EPISODE_APPROVAL,
        EpisodeStatus.EPISODE_APPROVAL,  EpisodeStatus.COMPLETED
    );

    /** 允许的驳回路径 */
    private static final Set<Map.Entry<EpisodeStatus, EpisodeStatus>> REJECTIONS = Set.of(
        Map.entry(EpisodeStatus.SCRIPT_REVIEW,    EpisodeStatus.SCRIPT_DRAFT),
        Map.entry(EpisodeStatus.EPISODE_APPROVAL, EpisodeStatus.GENERATING)
    );

    /**
     * 获取下一个合法状态
     *
     * @param current 当前状态
     * @return 下一步状态
     */
    public static EpisodeStatus next(EpisodeStatus current) {
        EpisodeStatus next = TRANSITIONS.get(current);
        if (next == null) {
            throw new IllegalStateException("当前状态无法流转: " + current);
        }
        return next;
    }

    /**
     * 校验从 from 到 to 的跳转是否合法
     */
    public static boolean canTransition(EpisodeStatus from, EpisodeStatus to) {
        EpisodeStatus expectedNext = TRANSITIONS.get(from);
        if (expectedNext == to) return true;
        return REJECTIONS.contains(Map.entry(from, to));
    }
}
