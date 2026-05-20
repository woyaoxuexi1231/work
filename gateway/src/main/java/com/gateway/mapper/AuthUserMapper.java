package com.gateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateway.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUser> {
}
