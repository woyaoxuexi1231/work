package com.example.dubbo.api;

import com.example.dubbo.api.model.ComplexType;
import com.example.dubbo.api.model.User;

import java.util.List;

/**
 * 用户服务接口 — Provider 和 Consumer 共用的契约。
 */
public interface UserService {

    /** 根据 ID 查用户 */
    User getUserById(Long id);

    /** 查全部（演示 List 泛型序列化） */
    List<User> listAll();

    /** 获取复杂数据（演示 Hessian2 泛型坑） */
    ComplexType getComplexData();

    /** 模拟慢查询（演示超时） */
    User slowQuery(Long id, long millis);
}
