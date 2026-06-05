package com.mlm.user.service;

import com.mlm.user.entity.User;
import com.mlm.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户服务 — 用户信息查询
 * <p>
 * 【职责】
 * 提供用户基本信息查询。用户认证和登录功能已迁移至外部认证网关，
 * 本服务仅用于内部查询（如阶段负责人选择时的用户信息展示）。
 *
 * @author mlm
 * @see com.mlm.user.controller.UserListController
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    /**
     * 构造用户服务
     *
     * @param userMapper 用户 Mapper
     */
    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 根据主键查询用户
     *
     * @param id 用户 ID
     * @return 用户实体，不存在时返回 null
     */
    public User getById(Long id) {
        return userMapper.selectById(id);
    }
}
