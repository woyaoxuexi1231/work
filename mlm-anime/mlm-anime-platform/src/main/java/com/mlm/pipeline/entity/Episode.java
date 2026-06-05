package com.mlm.pipeline.entity;

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
 * 剧集实体 — 对应数据库 episode 表
 * <p>
 * 剧集是 Pipeline 状态机的核心载体，每集独立走完完整的管线流程：
 * {@code SCRIPT_DRAFT → SCRIPT_REVIEW → STORYBOARD → GENERATING → EPISODE_APPROVAL → COMPLETED}
 * <p>
 * 【关键字段说明】
 * <ul>
 *   <li>status — Pipeline 主状态（int 码，对应 {@link com.mlm.common.enums.EpisodeStatus}）</li>
 *   <li>stepStatus — 步骤子状态（int 码，对应 {@link com.mlm.common.enums.StepStatus}）</li>
 *   <li>scriptContent — 用户原始的剧本内容</li>
 *   <li>storyboardContent — AI 生成的分镜 JSON</li>
 * </ul>
 *
 * @author mlm
 * @see com.mlm.common.enums.EpisodeStatus
 * @see com.mlm.pipeline.engine.StateMachine
 * @see com.mlm.pipeline.engine.PipelineEngine
 */
@Getter
@Setter
@ToString
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

    /** Pipeline 主状态（int 码，见 EpisodeStatus 枚举） */
    private Integer status = 2;

    /** 步骤子状态（int 码，-1=失败, 0=待处理, 1=成功, 2=处理中） */
    private Integer stepStatus = 0;

    /** 剧本原始内容（用户手动编写或提交） */
    private String scriptContent;

    /** 分镜 JSON 内容（AI 拆分镜生成） */
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
