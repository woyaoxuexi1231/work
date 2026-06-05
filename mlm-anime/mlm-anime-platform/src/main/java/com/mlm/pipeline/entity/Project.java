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
 * 项目实体 — 对应数据库 project 表
 * <p>
 * 项目是剧集（{@link Episode}）的容器，记录项目的名称、创建者、
 * 总集数和已完成集数等元信息。项目本身不走 Pipeline 状态机，
 * Pipeline 流转在 {@link Episode} 层级。
 * <p>
 * 【权限模型】
 * <ul>
 *   <li>公开项目（isPublic=true）— 系统内所有用户可见</li>
 *   <li>私有项目（isPublic=false）— 仅创建者可见</li>
 *   <li>阶段负责人 — 通过 {@link StageMember} 表关联</li>
 * </ul>
 *
 * @author mlm
 * @see Episode 剧集实体
 * @see StageMember 阶段成员实体
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("project")
public class Project {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String name;

    /** 可选的引用资源 ID（从资源库创建时传入） */
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
