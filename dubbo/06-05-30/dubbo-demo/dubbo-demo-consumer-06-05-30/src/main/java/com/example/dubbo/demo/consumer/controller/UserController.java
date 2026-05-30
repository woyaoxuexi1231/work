package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户查询 HTTP 接口——通过 @DubboReference 远程调用 Provider。
 */
@RestController
public class UserController {

    @DubboReference(
            version = "1.0.0",           // 必须与 Provider 匹配
            group = "demo",              // 必须与 Provider 匹配
            timeout = 5000,              // Consumer 端超时（覆盖 Provider 端）
            retries = 1,                 // 失败重试次数
            loadbalance = "random",      // 负载均衡
            cluster = "failover",        // 集群容错
            check = false,               // 启动时不检查 Provider
            // url = "dubbo://192.168.1.10:20880", // 直连（绕过注册中心）
            lazy = true,                 // 延迟连接（真正调用时才建立连接）
            sticky = false,              // 粘滞连接（尽量发往同一 Provider）
            sent = true,                 // 是否异步发送（true=等待结果）
            protocol = "dubbo",          // 指定协议
            mock = "force:return null",  // 服务降级
            stub = "com.example.UserServiceStub",  // 本地存根
            validation = "true",         // 参数验证
            cache = "lru",               // 结果缓存
            async = false,               // 是否异步调用（true 时返回 null）
            connections = 1,             // 连接数限制
            actives = 20,                // 每方法最大并发调用数
            tag = "gray"                 // 标签路由
    )
    private UserService userService;

    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }
}
