package com.mlm.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

/**
 * 阶段成员实体 — 对应数据库 project_stage_member 表
 * <p>
 * 定义项目中各阶段的负责人映射关系。
 * 用户在操作某阶段前，需通过 {@link com.mlm.common.util.StagePermissionUtil}
 * 校验其是否为该阶段的负责人（或项目创建者）。
 * <p>
 * 【阶段编码】
 * 使用 {@link com.mlm.common.enums.EpisodeStatus} 的 int code 作为阶段标识：
 * <ul>
 *   <li>2 = 剧本创作 (SCRIPT_DRAFT)</li>
 *   <li>3 = 剧本审核 (SCRIPT_REVIEW)</li>
 *   <li>4 = 拆分镜 (STORYBOARD)</li>
 *   <li>5 = AI 成片 (GENERATING)</li>
 *   <li>6 = 终审 (EPISODE_APPROVAL)</li>
 * </ul>
 *
 * @author mlm
 * @see com.mlm.common.util.StagePermissionUtil
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("project_stage_member")
public class StageMember {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 ID */
    private Long projectId;

    /** 阶段编码（EpisodeStatus 的 int code） */
    private Integer stage;

    /** 负责人用户 ID */
    private Long userId;
}
