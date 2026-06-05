package com.mlm.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.model.config.ModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型配置 Mapper — 基础 CRUD 操作
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动提供 insert/update/delete/select 方法。
 * 复杂查询使用 LambdaQueryWrapper 构造。
 *
 * @author mlm
 * @see ModelConfigEntity
 * @see com.mlm.model.config.ModelConfigLoader
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity> {
}
