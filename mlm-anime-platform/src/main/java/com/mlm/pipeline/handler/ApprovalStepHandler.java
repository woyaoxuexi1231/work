package com.mlm.pipeline.handler;

import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.StepStatus;
import com.mlm.notification.entity.ReviewMessage;
import com.mlm.notification.service.ReviewMessageService;
import com.mlm.pipeline.engine.StepHandler;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.mapper.EpisodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 成片终审步骤处理器 — EPISODE_APPROVAL
 * <p>
 * AI 成片完成后自动进入终审，插入审核消息等待人工终审。
 * 通过 → PipelineEngine.advance 推进到 COMPLETED（该集完成）。
 * 驳回 → PipelineEngine.reject 退回 GENERATING 重做。
 */
@Component
public class ApprovalStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(ApprovalStepHandler.class);
    private final EpisodeMapper episodeMapper;
    private final ReviewMessageService messageService;

    public ApprovalStepHandler(EpisodeMapper episodeMapper, ReviewMessageService messageService) {
        this.episodeMapper = episodeMapper;
        this.messageService = messageService;
    }

    @Override
    public EpisodeStatus step() { return EpisodeStatus.EPISODE_APPROVAL; }

    @Override
    public void handle(Episode episode) {
        episodeMapper.updateStatus(episode.getId(),
            EpisodeStatus.EPISODE_APPROVAL.name(), EpisodeStatus.EPISODE_APPROVAL.name(), StepStatus.PENDING.name());

        // 插入终审消息
        ReviewMessage msg = new ReviewMessage();
        msg.setEpisodeId(episode.getId());
        msg.setProjectId(episode.getProjectId());
        msg.setEpisodeNumber(episode.getEpisodeNumber());
        msg.setType("EPISODE_REVIEW");
        msg.setTitle("第" + episode.getEpisodeNumber() + "集 成片待终审");
        msg.setContent("项目 #" + episode.getProjectId() + " 第" + episode.getEpisodeNumber() + "集 AI 成片已完成，请终审。");
        messageService.create(msg);

        log.info(">>>>> EPISODE_APPROVAL: episodeId={}, projectId={}, #{}", episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());
    }
}
