package com.mlm.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.pipeline.entity.Project;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目 Mapper — 基础 CRUD 操作
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动提供 insert/update/delete/select 方法。
 * 项目实体自身的状态更新操作简单，复杂的状态流转逻辑在
 * {@link com.mlm.pipeline.mapper.EpisodeMapper} 中实现（CAS 乐观锁更新）。
 *
 * @author mlm
 * @see Project 项目实体
 * @see com.mlm.pipeline.service.ProjectService
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
