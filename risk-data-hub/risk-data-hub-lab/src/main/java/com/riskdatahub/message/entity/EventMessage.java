package com.riskdatahub.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件消息实体 — 发件箱模式中的持久化事件。
 * <p>
 * 对应数据库表 {@code event_message}，ETL 同步完成的事件先写入此表，
 * 后续可由独立的消息消费模块读取并投递到消息队列。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("event_message")
public class EventMessage {

    /** 消息 ID（Leaf 号段生成） */
    private Long messageId;

    /** 主题 */
    private String topic;

    /** 业务 key */
    private String bizKey;

    /** 消息体（JSON 格式） */
    private String payload;

    /** 消息状态（NEW / PROCESSED / FAILED） */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
