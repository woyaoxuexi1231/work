package com.example.dubbo.demo.provider.service.impl;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 用户服务实现 — 暴露为远程 RPC 服务。
 */
@DubboService(version = "1.0.0", group = "demo")
@Component
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Value("${dubbo.protocols.dubbo.port}")
    private String port;

    @Override
    public User getUserById(Long id) {
        log.info(">>> [Provider] getUserById({})", id);
        User user = new User(id, "用户" + id + "-" + port, "user" + id + "@example.com");
        log.info("<<< [Provider] 返回: {}", user);
        return user;
    }

    @Override
    public CompletableFuture<User> getUserByIdAsync(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("getUserByIdAsync");
            return getUserById(id);
        });
    }
}
