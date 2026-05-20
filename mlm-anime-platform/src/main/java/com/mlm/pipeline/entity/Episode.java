package com.mlm.pipeline.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.StepStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 剧集实体 — 对应数据库 episode 表
 * <p>
 * 每集独立走完完整的 Pipeline 管线：
 * SCRIPT_DRAFT → SCRIPT_REVIEW → STORYBOARD → GENERATING → EPISODE_APPROVAL → COMPLETED
 * <p>
 * 剧本一集一集提交和审核，审核通过自动触发拆分镜，
 * 分镜完成后自动进入 AI 成片（生图→生视频→合成），
 * 最终终审通过则该集完成。
 *
 * @see com.mlm.common.enums.EpisodeStatus
 * @see com.mlm.pipeline.engine.StateMachine
 */
@Data
@NoArgsConstructor
@TableName("episode")
public class Episode {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 集号（第几集） */
    private Integer episodeNumber;

    /** 本集标题 */
    private String title;

    /** Pipeline 主状态 */
    private EpisodeStatus status = EpisodeStatus.SCRIPT_DRAFT;

    /** 当前步骤的子状态：PENDING / PROCESSING / SUCCESS / FAILED */
    private StepStatus stepStatus = StepStatus.PENDING;

    /** 剧本原始内容（用户提交或 AI 生成） */
    private String scriptContent;

    /** 分镜 JSON 内容 */
    private String storyboardContent;

    /** 关联成片资源 ID */
    private Long resultResourceId;

    /** 当前步骤失败原因 */
    private String errorMsg;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
