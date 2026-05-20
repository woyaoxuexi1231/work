package com.mlm.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.user.entity.User;
import com.mlm.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 用户服务 — 登录校验、用户查询
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 根据用户名+密码校验登录，成功返回 User，失败返回 null */
    public User login(String username, String password) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getPassword, password)
        );
        if (user != null) {
            log.info("用户登录: {}", username);
        } else {
            log.warn("登录失败: {}", username);
        }
        return user;
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }
}
