package com.mlm.pipeline.engine;

import com.mlm.common.enums.EpisodeStatus;
import java.util.List;
import java.util.Set;

/**
 * Pipeline 状态机 — 定义剧集状态的合法流转路径（按值）
 * <p>
 * 主线流转：
 * <pre>
 * 2(剧本创作) → 3(审核) → 4(拆分镜) → 5(AI成片) → 6(终审) → 7(完成)
 * </pre>
 * 驳回：
 * <pre>
 * 3 → 2 (剧本驳回)  |  6 → 5 (终审驳回)
 * </pre>
 */
public class StateMachine {

    private static final List<EpisodeStatus> ORDER = List.of(
        EpisodeStatus.SCRIPT_DRAFT,      // 2
        EpisodeStatus.SCRIPT_REVIEW,     // 3
        EpisodeStatus.STORYBOARD,        // 4
        EpisodeStatus.GENERATING,        // 5
        EpisodeStatus.EPISODE_APPROVAL,  // 6
        EpisodeStatus.COMPLETED          // 7
    );

    private static final Set<Pair> REJECTIONS = Set.of(
        new Pair(EpisodeStatus.SCRIPT_REVIEW, EpisodeStatus.SCRIPT_DRAFT),
        new Pair(EpisodeStatus.EPISODE_APPROVAL, EpisodeStatus.GENERATING)
    );

    /** 获取下一步，不需要外抛异常了（状态机内部可判） */
    public static EpisodeStatus next(EpisodeStatus current) {
        if (current == EpisodeStatus.COMPLETED || current == EpisodeStatus.FAILED) {
            throw new IllegalStateException("当前状态无法流转: " + current);
        }
        int idx = ORDER.indexOf(current);
        if (idx < 0 || idx == ORDER.size() - 1) {
            throw new IllegalStateException("当前状态无法流转: " + current);
        }
        return ORDER.get(idx + 1);
    }

    public static boolean canTransition(EpisodeStatus from, EpisodeStatus to) {
        EpisodeStatus expected = ORDER.indexOf(from) < ORDER.size() - 1
            ? ORDER.get(ORDER.indexOf(from) + 1) : null;
        if (expected == to) return true;
        return REJECTIONS.contains(new Pair(from, to));
    }

    /** 简单 pair，内部使用 */
    private record Pair(EpisodeStatus from, EpisodeStatus to) {}
}
