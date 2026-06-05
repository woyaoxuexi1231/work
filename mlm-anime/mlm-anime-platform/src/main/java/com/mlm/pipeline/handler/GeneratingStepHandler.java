package com.mlm.pipeline.handler;

import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.StepStatus;
import com.mlm.pipeline.engine.StepHandler;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.mapper.EpisodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 【策略实现】AI 成片步骤处理器 — 对应 {@link EpisodeStatus#GENERATING}(5)
 * <p>
 * 【重要】本处理器不自动提交任何 AI 生成任务。
 * <p>
 * 【职责】
 * 将 stepStatus 置为 PENDING，等待用户在生成工作台手动操作：
 * <ol>
 *   <li>查看分镜列表</li>
 *   <li>逐场景选择生成图片（文生图）</li>
 *   <li>选择图片生成视频（图生视频）</li>
 *   <li>提交终审</li>
 * </ol>
 * <p>
 * 【设计决策 — 为什么不做自动生成？】
 * <ul>
 *   <li>AI 成片是<em>用户手动操作</em>的场景，不是自动流水线</li>
 *   <li>用户需要先查看分镜内容，再逐场景决定生成什么</li>
 *   <li>自动提交让用户失去对生成过程的控制感和调整空间</li>
 * </ul>
 *
 * @author mlm
 * @see StoryboardStepHandler 上一环节（拆分镜）
 * @see ApprovalStepHandler 下一环节（终审）
 */
@Component
public class GeneratingStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(GeneratingStepHandler.class);

    private final EpisodeMapper episodeMapper;

    /**
     * 构造 AI 成片处理器
     *
     * @param episodeMapper 剧集 Mapper（状态更新）
     */
    public GeneratingStepHandler(EpisodeMapper episodeMapper) {
        this.episodeMapper = episodeMapper;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 返回 {@link EpisodeStatus#GENERATING}。
     */
    @Override
    public EpisodeStatus step() {
        return EpisodeStatus.GENERATING;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【实现说明】
     * 仅将子状态置为 PENDING 以允许前端展示生成操作界面。
     * 不提交任何 AI 任务。生成操作由用户在前端手动触发。
     */
    @Override
    public void handle(Episode episode) {
        log.info("[StepHandler] GENERATING 进入 — episodeId={}, projectId={}, episodeNumber={}, "
                        + "等待用户在生成工作台操作",
                episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());

        episodeMapper.updateStatus(
                episode.getId(),
                EpisodeStatus.GENERATING.getCode(),
                EpisodeStatus.GENERATING.getCode(),
                StepStatus.PENDING.getCode()
        );

        log.info("[StepHandler] GENERATING 就绪 — episodeId={}", episode.getId());
    }
}
