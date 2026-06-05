package com.mlm.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper — 基础 CRUD 操作
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动提供 insert/update/delete/select 方法。
 * 用户认证已迁移至外部网关，本 Mapper 仅用于用户信息查询。
 *
 * @author mlm
 * @see User 用户实体
 * @see com.mlm.user.service.UserService
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
