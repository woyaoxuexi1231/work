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
 * 审核消息服务
 * <p>
 * 职责单一：消息的增、查（未读列表/计数）、标记已读。
 * 消息的写入时机在各 {@link com.mlm.pipeline.engine.StepHandler} 中。
 */
@Service
public class ReviewMessageService {

    private static final Logger log = LoggerFactory.getLogger(ReviewMessageService.class);

    private final ReviewMessageMapper mapper;

    public ReviewMessageService(ReviewMessageMapper mapper) {
        this.mapper = mapper;
    }

    /** 插入一条审核消息 */
    public ReviewMessage create(ReviewMessage msg) {
        mapper.insert(msg);
        log.info("审核消息已创建: type={}, episodeId={}", msg.getType(), msg.getEpisodeId());
        return msg;
    }

    /** 查询所有未读消息（按创建时间倒序） */
    public List<ReviewMessage> findUnread() {
        return mapper.selectList(
            new LambdaQueryWrapper<ReviewMessage>()
                .eq(ReviewMessage::getIsRead, false)
                .orderByDesc(ReviewMessage::getCreatedAt)
        );
    }

    /** 未读消息数量 */
    public long countUnread() {
        return mapper.selectCount(
            new LambdaQueryWrapper<ReviewMessage>()
                .eq(ReviewMessage::getIsRead, false)
        );
    }

    /** 标记指定 ID 的消息为已读 */
    public void markAsRead(Long... ids) {
        if (ids.length == 0) return;
        String idStr = Arrays.stream(ids).map(String::valueOf).collect(Collectors.joining(","));
        mapper.markAsRead(idStr);
        log.debug("标记已读: ids={}", idStr);
    }

    /** 标记指定剧集的所有消息为已读 */
    public void markReadByEpisode(Long episodeId) {
        mapper.selectList(
            new LambdaQueryWrapper<ReviewMessage>().eq(ReviewMessage::getEpisodeId, episodeId)
        ).forEach(msg -> mapper.markAsRead(String.valueOf(msg.getId())));
    }
}
