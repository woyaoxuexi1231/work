package com.example.dubbo.consumer.controller;

import com.example.dubbo.api.UserService;
import com.example.dubbo.api.model.ComplexType;
import com.example.dubbo.api.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * UserService 的 Mock 降级实现。
 *
 * 规则：
 *   - 实现同一接口 UserService
 *   - 返回无害的默认值（空列表、提示信息）
 *   - 命名约定：接口名 + Mock → Dubbo 用 mock="true" 时自动发现
 *
 * 触发条件：RPC 超时 / 无可用节点 / 网络异常 / 所有重试耗尽
 */
public class UserServiceMock implements UserService {

    @Override
    public User getUserById(Long id) {
        System.out.println("[Mock] RPC 失败，兜底 getUserById(" + id + ")");
        User fallback = new User(-1L, "系统繁忙，请稍后重试", 0);
        fallback.setEmail("fallback@mock.local");
        fallback.setCreateTime(new Date());
        return fallback;
    }

    @Override
    public List<User> listAll() {
        System.out.println("[Mock] RPC 失败，返回空列表");
        return Collections.emptyList();
    }

    @Override
    public ComplexType getComplexData() {
        System.out.println("[Mock] RPC 失败，返回空对象");
        ComplexType fallback = new ComplexType();
        fallback.setDepartments(new ArrayList<>());
        return fallback;
    }

    @Override
    public User slowQuery(Long id, long millis) {
        System.out.println("[Mock] RPC 失败（超时），兜底 slowQuery(" + id + ", " + millis + "ms)");
        return new User(-1L, "查询超时，请重试", 0);
    }
}
