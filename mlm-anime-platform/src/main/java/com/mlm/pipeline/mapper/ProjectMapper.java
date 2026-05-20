package com.mlm.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.pipeline.entity.Project;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目 Mapper — 基础 CRUD
 * <p>
 * 状态更新操作已移至 {@link com.mlm.pipeline.mapper.EpisodeMapper}。
 *
 * @see com.mlm.pipeline.entity.Project
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
