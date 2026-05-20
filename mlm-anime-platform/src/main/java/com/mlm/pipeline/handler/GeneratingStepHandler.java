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
 * 【策略实现】AI 成片步骤处理器 — 对应 EpisodeStatus.GENERATING(5)
 * <p>
 * 【重要】不自动提交任何 AI 任务。职责仅限于将 stepStatus 置为 PENDING。
 * <p>
 * 为什么不做自动提交？
 * <ul>
 *   <li>AI 成片是<em>用户手动操作</em>的场景，不是自动流水线</li>
 *   <li>用户需要先查看分镜列表，再逐场景决定生成什么</li>
 *   <li>自动提交让用户失去对生成过程的控制感</li>
 * </ul>
 * 后续流程由前端生成工作台驱动：生成图片 → 生成视频 → 提交终审。
 */
@Component
public class GeneratingStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(GeneratingStepHandler.class);
    private final EpisodeMapper episodeMapper;

    public GeneratingStepHandler(EpisodeMapper episodeMapper) {
        this.episodeMapper = episodeMapper;
    }

    @Override
    public EpisodeStatus step() { return EpisodeStatus.GENERATING; }

    @Override
    public void handle(Episode episode) {
        log.info(">>>>> GENERATING 进入: episodeId={}, projectId={}, 等待用户在生成工作台操作",
            episode.getId(), episode.getProjectId());
        episodeMapper.updateStatus(episode.getId(),
            EpisodeStatus.GENERATING.getCode(), EpisodeStatus.GENERATING.getCode(), StepStatus.PENDING.getCode());
    }
}
