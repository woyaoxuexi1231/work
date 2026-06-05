package com.riskdatahub.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.entity.EventMessage;
import com.riskdatahub.message.mapper.EventMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息发件箱服务 — 将 ETL 同步完成事件持久化到 event_message 表。
 * <p>
 * 采用「发件箱模式」（Outbox Pattern）：同步事件先写入数据库，
 * 后续可由独立的消息消费模块读取并投递到消息队列。
 * 当前仅实现写入端，消费端预留。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageOutboxService {

    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final LeafSegmentService leafSegmentService;
    private final EventMessageMapper eventMessageMapper;

    /**
     * 发布一条事件消息到发件箱。
     *
     * @param topic   主题
     * @param bizKey  业务 key
     * @param payload 消息体（JSON）
     * @return 消息 ID
     */
    public long publish(String topic, String bizKey, String payload) {
        long messageId = leafSegmentService.nextId("event_message");
        log.info("[消息模块] 生成标准事件 messageId={}, topic={}, bizKey={}", messageId, topic, bizKey);
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            EventMessage message = new EventMessage();
            message.setMessageId(messageId);
            message.setTopic(topic);
            message.setBizKey(bizKey);
            message.setPayload(payload);
            message.setStatus("NEW");
            message.setCreatedAt(TimeUtils.now());
            eventMessageMapper.insert(message);
        });
        return messageId;
    }

    /**
     * 查询最近 20 条事件消息。
     *
     * @return 事件消息列表
     */
    public List<EventMessage> recentMessages() {
        return routingMybatisExecutor.query(HubConstants.DS_HUB,
                () -> eventMessageMapper.selectList(new LambdaQueryWrapper<EventMessage>()
                        .orderByDesc(EventMessage::getMessageId)
                        .last("limit 20")));
    }
}
