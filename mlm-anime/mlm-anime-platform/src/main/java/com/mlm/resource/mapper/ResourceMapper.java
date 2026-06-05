package com.mlm.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.resource.entity.Resource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源 Mapper — 基础 CRUD 操作
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动提供 insert/update/delete/select 方法。
 *
 * @author mlm
 * @see Resource 资源实体
 * @see com.mlm.resource.service.ResourceService
 */
@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {
}
