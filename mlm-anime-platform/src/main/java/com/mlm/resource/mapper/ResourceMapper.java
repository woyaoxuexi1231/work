package com.mlm.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.resource.entity.Resource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源 Mapper — 基础 CRUD
 *
 * @see com.mlm.resource.entity.Resource
 */
@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {
}
