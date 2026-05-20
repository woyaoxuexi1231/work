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
 * AI 成片步骤处理器 — GENERATING
 * <p>
 * 不自动提交任何 AI 任务。职责仅限于：
 * <ol>
 *   <li>将 stepStatus 置为 PENDING（等待用户在前端操作）</li>
 *   <li>由前端生成工作台页面驱动后续的「生成图片→生成视频→提交终审」</li>
 * </ol>
 * 这样设计的理由：
 * <ul>
 *   <li>AI 成片是<em>用户手动操作</em>的场景而非自动流程</li>
 *   <li>用户需要先看到分镜列表，再逐场景决定生成什么</li>
 *   <li>自动提交会让用户失去控制感，且无法选择生成参数</li>
 * </ul>
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
