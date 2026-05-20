package com.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gateway.entity.AuthUser;
import com.gateway.mapper.AuthUserMapper;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthUserMapper authUserMapper;

    public AuthService(AuthUserMapper authUserMapper) {
        this.authUserMapper = authUserMapper;
    }

    public AuthUser login(String username, String password) {
        return authUserMapper.selectOne(
            new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getUsername, username)
                .eq(AuthUser::getPassword, password)
        );
    }

    public AuthUser getById(Long id) {
        return authUserMapper.selectById(id);
    }
}
