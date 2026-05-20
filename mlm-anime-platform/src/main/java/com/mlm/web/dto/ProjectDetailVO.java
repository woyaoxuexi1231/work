package com.mlm.web.dto;

import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 项目详情响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDetailVO {
    private Project project;
    private List<Episode> episodes;
}
