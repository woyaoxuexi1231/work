package com.mlm.web.dto;

import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 项目详情 VO — 包含项目信息和剧集列表
 *
 * @author mlm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDetailVO {
    /** 项目信息 */
    private Project project;
    /** 项目的所有剧集列表 */
    private List<Episode> episodes;
}
