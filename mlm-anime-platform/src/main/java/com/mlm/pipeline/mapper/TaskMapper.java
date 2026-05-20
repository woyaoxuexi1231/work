package com.mlm.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.pipeline.entity.Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper — 基础 CRUD
 *
 * @see com.mlm.pipeline.entity.Task
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
