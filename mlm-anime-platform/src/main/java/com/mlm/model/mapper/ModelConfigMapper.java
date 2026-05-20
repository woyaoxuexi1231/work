package com.mlm.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.model.config.ModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型配置 Mapper — 基础 CRUD
 *
 * @see com.mlm.model.config.ModelConfigEntity
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity> {
}
