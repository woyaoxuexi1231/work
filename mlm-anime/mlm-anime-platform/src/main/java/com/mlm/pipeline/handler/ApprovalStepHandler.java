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
 * 【策略实现】成片终审步骤处理器 — 对应 {@link EpisodeStatus#EPISODE_APPROVAL}(6)
 * <p>
 * 【职责】
 * AI 成片完成后进入终审，由用户点击「提交终审」触发。
 * <ul>
 *   <li>更新 stepStatus 为 PENDING（等待终审操作）</li>
 *   <li>插入 {@code EPISODE_REVIEW} 类型审核消息，前端铃铛通知</li>
 *   <li>通过 → {@link com.mlm.pipeline.engine.PipelineEngine#advance} 推进到 COMPLETED</li>
 *   <li>驳回 → {@link com.mlm.pipeline.engine.PipelineEngine#reject} 退回 GENERATING 重做 AI 生成</li>
 * </ul>
 *
 * @author mlm
 * @see GeneratingStepHandler 上一环节（AI成片）
 * @see com.mlm.pipeline.engine.PipelineEngine#completeEpisode 终态处理
 */
@Component
public class ApprovalStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(ApprovalStepHandler.class);

    private static final String REVIEW_TYPE = "EPISODE_REVIEW";
    private static final String REVIEW_TITLE_TEMPLATE = "第%d集 成片待终审";
    private static final String REVIEW_CONTENT_TEMPLATE = "项目 #%d 第%d集 AI 成片已完成，请终审。";

    private final EpisodeMapper episodeMapper;
    private final ReviewMessageService messageService;

    /**
     * 构造终审处理器
     *
     * @param episodeMapper  剧集 Mapper（状态更新）
     * @param messageService 审核消息服务
     */
    public ApprovalStepHandler(EpisodeMapper episodeMapper,
                               ReviewMessageService messageService) {
        this.episodeMapper = episodeMapper;
        this.messageService = messageService;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 返回 {@link EpisodeStatus#EPISODE_APPROVAL}。
     */
    @Override
    public EpisodeStatus step() {
        return EpisodeStatus.EPISODE_APPROVAL;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【实现说明】
     * 更新子状态为 PENDING，创建终审通知消息。
     * 终审操作由前端主动调用 API 触发，通过或驳回均在本 Handler 外处理。
     */
    @Override
    public void handle(Episode episode) {
        log.info("[StepHandler] EPISODE_APPROVAL 开始 — episodeId={}, projectId={}, episodeNumber={}",
                episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());

        // 更新子状态为 PENDING（等待终审操作）
        episodeMapper.updateStatus(
                episode.getId(),
                EpisodeStatus.EPISODE_APPROVAL.getCode(),
                EpisodeStatus.EPISODE_APPROVAL.getCode(),
                StepStatus.PENDING.getCode()
        );

        // 创建终审通知消息
        ReviewMessage msg = buildReviewMessage(episode);
        messageService.create(msg);

        log.info("[StepHandler] EPISODE_APPROVAL 完成 — episodeId={}, reviewMessageId={}",
                episode.getId(), msg.getId());
    }

    /**
     * 构建终审通知消息
     *
     * @param episode 当前剧集
     * @return 审核消息实体
     */
    private ReviewMessage buildReviewMessage(Episode episode) {
        ReviewMessage msg = new ReviewMessage();
        msg.setEpisodeId(episode.getId());
        msg.setProjectId(episode.getProjectId());
        msg.setEpisodeNumber(episode.getEpisodeNumber());
        msg.setType(REVIEW_TYPE);
        msg.setTitle(String.format(REVIEW_TITLE_TEMPLATE, episode.getEpisodeNumber()));
        msg.setContent(String.format(REVIEW_CONTENT_TEMPLATE,
                episode.getProjectId(), episode.getEpisodeNumber()));
        return msg;
    }
}
