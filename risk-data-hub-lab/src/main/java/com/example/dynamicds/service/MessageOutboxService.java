package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.EventMessage;
import com.example.dynamicds.mapper.EventMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 消息发件箱服务 — 将 ETL 同步完成事件持久化到 event_message 表。
 *
 * 当前采用「发件箱模式」（Outbox Pattern）：同步事件先写入数据库，
 * 后续可由独立的消息消费模块读取并投递到消息队列。
 * 当前仅实现写入端，消费端预留。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageOutboxService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final LeafSegmentService leafSegmentService;
    private final EventMessageMapper eventMessageMapper;

    public long publish(String topic, String bizKey, String payload) {
        long messageId = leafSegmentService.nextId("event_message");
        log.info("[消息模块] 生成标准事件 messageId={}, topic={}, bizKey={}", messageId, topic, bizKey);
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> {
            EventMessage message = new EventMessage();
            message.setMessageId(messageId);
            message.setTopic(topic);
            message.setBizKey(bizKey);
            message.setPayload(payload);
            message.setStatus("NEW");
            message.setCreatedAt(LocalDateTime.now().format(FORMATTER));
            eventMessageMapper.insert(message);
        });
        return messageId;
    }

    public List<EventMessage> recentMessages() {
        return routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB,
                () -> eventMessageMapper.selectList(new LambdaQueryWrapper<EventMessage>()
                        .orderByDesc(EventMessage::getMessageId)
                        .last("limit 20")));
    }
}
