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
 * 【策略实现】剧本审核步骤处理器 — 对应 {@link EpisodeStatus#SCRIPT_REVIEW}(3)
 * <p>
 * 【职责】
 * 剧集进入审核状态时，将 stepStatus 置为 PENDING 以允许前端显示审核操作按钮，
 * 并创建审核通知消息供前端铃铛拉取。
 * <p>
 * 【处理流程】
 * <ol>
 *   <li>更新 stepStatus 为 PENDING（等待人工审核）</li>
 *   <li>插入 {@code SCRIPT_REVIEW} 类型审核消息到 review_message 表</li>
 *   <li>人工通过 → {@link com.mlm.pipeline.engine.PipelineEngine#advance} 推进到 STORYBOARD</li>
 *   <li>人工驳回 → {@link com.mlm.pipeline.engine.PipelineEngine#reject} 退回 SCRIPT_DRAFT</li>
 * </ol>
 *
 * @author mlm
 * @see DraftStepHandler 上一环节（剧本创作）
 * @see StoryboardStepHandler 下一环节（拆分镜）
 * @see ReviewMessage 审核消息实体
 */
@Component
public class ReviewStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(ReviewStepHandler.class);

    private static final String REVIEW_TYPE = "SCRIPT_REVIEW";
    private static final String REVIEW_TITLE_TEMPLATE = "第%d集 剧本待审核";
    private static final String REVIEW_CONTENT_TEMPLATE = "项目 #%d 第%d集 剧本已提交，请审核。";

    private final EpisodeMapper episodeMapper;
    private final ReviewMessageService messageService;

    /**
     * 构造剧本审核处理器
     *
     * @param episodeMapper  剧集 Mapper（状态更新）
     * @param messageService 审核消息服务
     */
    public ReviewStepHandler(EpisodeMapper episodeMapper,
                             ReviewMessageService messageService) {
        this.episodeMapper = episodeMapper;
        this.messageService = messageService;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 返回 {@link EpisodeStatus#SCRIPT_REVIEW}。
     */
    @Override
    public EpisodeStatus step() {
        return EpisodeStatus.SCRIPT_REVIEW;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【实现说明】
     * 更新子状态为 PENDING，然后创建剧本审核通知消息。
     * Handler 不阻塞等待人工审核，审核操作由前端主动调用 API 触发。
     */
    @Override
    public void handle(Episode episode) {
        log.info("[StepHandler] SCRIPT_REVIEW 开始 — episodeId={}, projectId={}, episodeNumber={}",
                episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());

        // 更新子状态为 PENDING（等待人工操作）
        episodeMapper.updateStatus(
                episode.getId(),
                EpisodeStatus.SCRIPT_REVIEW.getCode(),
                EpisodeStatus.SCRIPT_REVIEW.getCode(),
                StepStatus.PENDING.getCode()
        );

        // 创建剧本审核通知消息
        ReviewMessage msg = buildReviewMessage(episode);
        messageService.create(msg);

        log.info("[StepHandler] SCRIPT_REVIEW 完成 — episodeId={}, reviewMessageId={}",
                episode.getId(), msg.getId());
    }

    /**
     * 构建剧本审核消息
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
