package com.mlm.user.service;

import com.mlm.user.entity.User;
import com.mlm.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户服务 — 用户查询（登录已迁移至 Gateway）
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }
}
