package com.mlm.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.pipeline.entity.Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper — 基础 CRUD 操作
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动提供 insert/update/delete/select 方法。
 * 任务状态的轮询和更新由 {@link com.mlm.model.core.ModelGateway} 管理。
 *
 * @author mlm
 * @see Task 任务实体
 * @see com.mlm.pipeline.service.TaskService
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
