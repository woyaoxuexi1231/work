package com.mlm.pipeline.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目实体 — 对应数据库 project 表
 * <p>
 * 项目是剧集的容器，本身不走 Pipeline，真正的状态流转在 {@link Episode} 中。
 * 项目可引用一个资源（从资源库创建时），无资源则为直接创建。
 */
@Data
@NoArgsConstructor
@TableName("project")
public class Project {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String name;

    /** 可选的引用资源ID（从资源库创建时传入） */
    private Long resourceId;

    /** 总集数 */
    private Integer episodesCount = 0;

    /** 已完成集数 */
    private Integer completedCount = 0;

    /** 创建者用户 ID */
    private Long createdBy;

    /** 是否公开项目 */
    private Boolean isPublic = true;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
