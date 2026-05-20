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
 * 剧本审核步骤处理器 — SCRIPT_REVIEW
 * <p>
 * 进入审核时插入一条审核消息，等待人工审核剧本。
 * 通过 → PipelineEngine.advance 推进到 STORYBOARD（自动拆分镜）。
 * 驳回 → PipelineEngine.reject 退回 SCRIPT_DRAFT（重写）。
 */
@Component
public class ReviewStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(ReviewStepHandler.class);
    private final EpisodeMapper episodeMapper;
    private final ReviewMessageService messageService;

    public ReviewStepHandler(EpisodeMapper episodeMapper, ReviewMessageService messageService) {
        this.episodeMapper = episodeMapper;
        this.messageService = messageService;
    }

    @Override
    public EpisodeStatus step() { return EpisodeStatus.SCRIPT_REVIEW; }

    @Override
    public void handle(Episode episode) {
        episodeMapper.updateStatus(episode.getId(),
            EpisodeStatus.SCRIPT_REVIEW.getCode(), EpisodeStatus.SCRIPT_REVIEW.getCode(), StepStatus.PENDING.getCode());

        // 插入审核消息
        ReviewMessage msg = new ReviewMessage();
        msg.setEpisodeId(episode.getId());
        msg.setProjectId(episode.getProjectId());
        msg.setEpisodeNumber(episode.getEpisodeNumber());
        msg.setType("SCRIPT_REVIEW");
        msg.setTitle("第" + episode.getEpisodeNumber() + "集 剧本待审核");
        msg.setContent("项目 #" + episode.getProjectId() + " 第" + episode.getEpisodeNumber() + "集 剧本已提交，请审核。");
        messageService.create(msg);

        log.info(">>>>> SCRIPT_REVIEW: episodeId={}, projectId={}, #{}", episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());
    }
}
