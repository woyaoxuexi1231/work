package com.mlm.notification.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审核消息实体 — 对应数据库 review_message 表
 * <p>
 * 当剧集进入待审核/待终审状态时，由对应的 StepHandler 插入一条消息。
 * 前端通过消息铃铛（通知中心）查询未读消息，点击后标记已读。
 * <p>
 * 【消息类型】
 * <ul>
 *   <li>SCRIPT_REVIEW — 剧本待审核（由 {@link com.mlm.pipeline.handler.ReviewStepHandler} 创建）</li>
 *   <li>EPISODE_REVIEW — 成片待终审（由 {@link com.mlm.pipeline.handler.ApprovalStepHandler} 创建）</li>
 * </ul>
 * <p>
 * 【设计决策 — 为什么不用 MQ？】
 * 审核消息本质是<em>通知 (Notification)</em> 而非<em>命令 (Command)</em>，
 * 用数据库表足够承载。MQ 的真正价值在解耦<em>命令生产者与消费者</em>，
 * 而此处只是"谁提交了审核 → 通知前端去查看"，一个简单的 publish-subscribe
 * 用 WebSocket 或 SSE 更合适，此处先用数据库轮询保持简单。
 *
 * @author mlm
 * @see com.mlm.notification.service.ReviewMessageService
 * @see com.mlm.notification.controller.ReviewMessageController
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("review_message")
public class ReviewMessage {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联剧集 ID */
    private Long episodeId;

    /** 关联项目 ID（冗余字段，避免联查） */
    private Long projectId;

    /** 集号（冗余字段） */
    private Integer episodeNumber;

    /** 消息类型：SCRIPT_REVIEW / EPISODE_REVIEW */
    private String type;

    /** 消息标题（如 "第3集 剧本待审核"） */
    private String title;

    /** 消息正文 */
    private String content;

    /** 是否已读（false=未读, true=已读） */
    private Boolean isRead = false;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
