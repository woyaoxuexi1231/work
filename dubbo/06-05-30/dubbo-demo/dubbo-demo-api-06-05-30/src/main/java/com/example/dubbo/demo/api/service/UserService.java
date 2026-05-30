package com.example.dubbo.demo.api.service;

import com.example.dubbo.demo.api.model.User;

/**
 * 用户服务接口——Dubbo 远程调用的契约。
 */
public interface UserService {

    /** 根据 ID 查询用户 */
    User getUserById(Long id);
}
