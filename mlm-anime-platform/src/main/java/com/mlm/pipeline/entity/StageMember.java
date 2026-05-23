package com.mlm.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@Getter
@Setter
@ToString
@NoArgsConstructor
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
