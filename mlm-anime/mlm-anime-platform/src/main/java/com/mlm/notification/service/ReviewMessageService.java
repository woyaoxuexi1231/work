package com.mlm.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.notification.entity.ReviewMessage;
import com.mlm.notification.mapper.ReviewMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审核消息服务 — 消息的创建、查询和已读标记
 * <p>
 * 【职责】
 * <ul>
 *   <li>创建审核消息（由各 StepHandler 在进入审核态时调用）</li>
 *   <li>查询未读消息列表及数量（供控制器响应前端铃铛）</li>
 *   <li>批量/按剧集标记已读</li>
 * </ul>
 * <p>
 * 消息的写入时机在各 {@link com.mlm.pipeline.engine.StepHandler} 中，
 * 此服务仅提供数据库层面的操作。
 *
 * @author mlm
 * @see ReviewMessage 审核消息实体
 * @see com.mlm.notification.controller.ReviewMessageController
 */
@Service
public class ReviewMessageService {

    private static final Logger log = LoggerFactory.getLogger(ReviewMessageService.class);

    private final ReviewMessageMapper mapper;

    /**
     * 构造审核消息服务
     *
     * @param mapper 审核消息 Mapper
     */
    public ReviewMessageService(ReviewMessageMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 插入一条审核消息
     *
     * @param msg 审核消息实体
     * @return 创建后的消息实体（含自增 ID）
     */
    public ReviewMessage create(ReviewMessage msg) {
        mapper.insert(msg);
        log.info("审核消息已创建: type={}, episodeId={}, projectId={}",
                msg.getType(), msg.getEpisodeId(), msg.getProjectId());
        return msg;
    }

    /**
     * 查询所有未读消息（按创建时间倒序）
     *
     * @return 未读消息列表
     */
    public List<ReviewMessage> findUnread() {
        return mapper.selectList(
                new LambdaQueryWrapper<ReviewMessage>()
                        .eq(ReviewMessage::getIsRead, false)
                        .orderByDesc(ReviewMessage::getCreatedAt)
        );
    }

    /**
     * 查询未读消息数量
     *
     * @return 未读消息总数
     */
    public long countUnread() {
        return mapper.selectCount(
                new LambdaQueryWrapper<ReviewMessage>()
                        .eq(ReviewMessage::getIsRead, false)
        );
    }

    /**
     * 标记指定 ID 的消息为已读
     * <p>
     * 使用批量更新接口（PreparedStatement 参数绑定），防止 SQL 注入。
     *
     * @param ids 要标记为已读的消息 ID 数组
     */
    public void markAsRead(Long... ids) {
        if (ids.length == 0) return;
        List<Long> idList = Arrays.asList(ids);
        mapper.markAsReadBatch(idList);
        log.debug("消息标记已读: ids={}", idList);
    }

    /**
     * 标记指定剧集的所有消息为已读
     *
     * @param episodeId 剧集 ID
     */
    public void markReadByEpisode(Long episodeId) {
        List<Long> ids = mapper.selectList(
                new LambdaQueryWrapper<ReviewMessage>()
                        .eq(ReviewMessage::getEpisodeId, episodeId)
        ).stream().map(ReviewMessage::getId).collect(Collectors.toList());

        if (!ids.isEmpty()) {
            mapper.markAsReadBatch(ids);
            log.debug("剧集消息已标记已读: episodeId={}, count={}", episodeId, ids.size());
        }
    }
}
