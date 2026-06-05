package com.mlm.user.controller;

import com.mlm.common.result.ApiResult;
import com.mlm.user.entity.User;
import com.mlm.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户查询控制器 — 提供用户列表供前端选择阶段负责人
 * <p>
 * 【职责】
 * 返回系统中所有用户的列表，用于前端在设置阶段负责人时
 * 展示可选用户下拉列表。
 * <p>
 * 用户认证和登录已迁移至外部认证网关，本控制器仅提供查询。
 *
 * @author mlm
 */
@RestController
public class UserListController {

    private static final Logger log = LoggerFactory.getLogger(UserListController.class);

    private final UserMapper userMapper;

    /**
     * 构造用户查询控制器
     *
     * @param userMapper 用户 Mapper
     */
    public UserListController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 查询所有用户列表
     *
     * @return 用户列表
     */
    @PostMapping("/api/users/list")
    public ApiResult<List<User>> list() {
        List<User> users = userMapper.selectList(null);
        log.debug("用户列表查询: count={}", users.size());
        return ApiResult.ok(users);
    }
}
