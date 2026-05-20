package com.mlm.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 项目阶段负责人 — 对应 project_stage_member 表
 * <p>
 * 每个项目的每个阶段可以指定一个负责人，只有负责人能操作该阶段的剧集。
 */
@Data
@TableName("project_stage_member")
public class StageMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 ID */
    private Long projectId;

    /** 阶段编码（EpisodeStatus 的 int code：2=剧本创作, 3=审核, 4=拆分镜, 5=AI成片, 6=终审） */
    private Integer stage;

    /** 负责人用户 ID */
    private Long userId;
}
