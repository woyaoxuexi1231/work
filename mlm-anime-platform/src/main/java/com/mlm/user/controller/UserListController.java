package com.mlm.user.controller;

import com.mlm.common.dto.ApiResult;
import com.mlm.user.entity.User;
import com.mlm.user.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户查询接口 — 用于前端选择阶段负责人
 */
@RestController
public class UserListController {

    private final UserMapper userMapper;

    public UserListController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/api/users")
    public ApiResult<List<User>> list() {
        return ApiResult.ok(userMapper.selectList(null));
    }
}
