package com.mlm.pipeline.engine;

import com.mlm.common.enums.EpisodeStatus;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline 状态机 — 定义剧集状态的合法流转路径
 * <p>
 * <b>主线流转（顺序 2→3→4→5→6→7）：</b>
 * <pre>
 * 2(剧本创作) → 3(剧本审核) → 4(拆分镜) → 5(AI成片) → 6(终审) → 7(完成)
 * </pre>
 * <b>驳回路径：</b>
 * <pre>
 * 3(审核) → 2(剧本创作)  |  6(终审) → 5(AI成片)
 * </pre>
 * <p>
 * 状态流转校验通过两个方法实现：
 * <ul>
 *   <li>{@link #next(EpisodeStatus)} — 正向推进，获取下一状态</li>
 *   <li>{@link #canTransition(EpisodeStatus, EpisodeStatus)} — 任意方向（推进 + 驳回）</li>
 * </ul>
 * <p>
 * 【设计说明】
 * <p>
 * 使用线性列表定义主流程顺序，用集合定义例外（驳回）路径。
 * 新增状态只需修改 ORDER 列表和 REJECTIONS 集合，无需改动逻辑代码。
 * 符合开闭原则（Open-Closed Principle）。
 *
 * @author mlm
 * @see EpisodeStatus
 * @see PipelineEngine
 */
public final class StateMachine {

    /**
     * 主流程状态顺序列表
     * <p>
     * 索引位置决定流转方向：ORDER[i] → ORDER[i+1]。
     * 修改此列表时需同步更新允许的驳回路径 {@link #REJECTIONS}。
     */
    private static final List<EpisodeStatus> ORDER = Arrays.asList(
            EpisodeStatus.SCRIPT_DRAFT,      // 剧本创作 (2)
            EpisodeStatus.SCRIPT_REVIEW,     // 剧本审核 (3)
            EpisodeStatus.STORYBOARD,        // 拆分镜   (4)
            EpisodeStatus.GENERATING,        // AI成片   (5)
            EpisodeStatus.EPISODE_APPROVAL,  // 终审     (6)
            EpisodeStatus.COMPLETED          // 已完成   (7)
    );

    /**
     * 允许的驳回路径集合
     * <p>
     * Key = 当前状态, Value = 驳回目标状态。
     * 目前支持的驳回路径：
     * <ul>
     *   <li>SCRIPT_REVIEW(3) → SCRIPT_DRAFT(2)：剧本驳回重写</li>
     *   <li>EPISODE_APPROVAL(6) → GENERATING(5)：成片驳回重做</li>
     * </ul>
     */
    private static final Set<Map.Entry<EpisodeStatus, EpisodeStatus>> REJECTIONS = new HashSet<>(Arrays.asList(
            new AbstractMap.SimpleImmutableEntry<>(EpisodeStatus.SCRIPT_REVIEW, EpisodeStatus.SCRIPT_DRAFT),
            new AbstractMap.SimpleImmutableEntry<>(EpisodeStatus.EPISODE_APPROVAL, EpisodeStatus.GENERATING)
    ));

    private StateMachine() {
        // 工具类，禁止实例化
    }

    /**
     * 获取指定状态的下一合法状态
     * <p>
     * 沿 {@link #ORDER} 列表正向推进一步。终态（COMPLETED）和
     * 异常态（FAILED）不允许推进。
     *
     * @param current 当前状态
     * @return 下一状态
     * @throws IllegalStateException 当前状态为终态或不在 ORDER 列表中时抛出
     */
    public static EpisodeStatus next(EpisodeStatus current) {
        if (current == EpisodeStatus.COMPLETED || current == EpisodeStatus.FAILED) {
            throw new IllegalStateException("当前状态无法流转: " + current.getLabel());
        }
        int idx = ORDER.indexOf(current);
        if (idx < 0) {
            throw new IllegalStateException("未知状态: " + current);
        }
        if (idx >= ORDER.size() - 1) {
            throw new IllegalStateException("已是终态，无法继续推进: " + current.getLabel());
        }
        return ORDER.get(idx + 1);
    }

    /**
     * 判断两个状态之间是否允许流转（正向推进或驳回）
     * <p>
     * 先检查是否为正向前进（ORDER[i] → ORDER[i+1]），
     * 再检查是否属于允许的驳回路径。
     *
     * @param from 起始状态
     * @param to   目标状态
     * @return true=允许流转
     */
    public static boolean canTransition(EpisodeStatus from, EpisodeStatus to) {
        // 检查正向推进
        int fromIndex = ORDER.indexOf(from);
        if (fromIndex >= 0 && fromIndex < ORDER.size() - 1) {
            if (ORDER.get(fromIndex + 1) == to) {
                return true;
            }
        }
        // 检查驳回路径
        return REJECTIONS.contains(new AbstractMap.SimpleImmutableEntry<>(from, to));
    }
}
