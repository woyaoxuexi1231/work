package com.example.dubbo.demo.provider.service.impl;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 用户服务实现——标注 @DubboService 暴露为远程服务。
 */
@DubboService(
        version = "1.0.0",          // 版本号，多版本共存时使用
        group = "demo",             // 分组，同接口不同实现隔离
        timeout = 3000,             // 方法调用超时（毫秒）
        retries = 2,                // 失败重试次数（不含首次）
        weight = 100,               // 负载均衡权重（默认 100）
        loadbalance = "random",     // 负载均衡策略
        cluster = "failover",       // 集群容错策略
        actives = 0,                // 每连接最大并发数（0=不限制）
        executes = 0,               // 服务端最大并发执行数（0=不限制）
        connections = 0,            // 每个 Consumer 的最大连接数
        delay = -1,                 // 延迟暴露（毫秒，-1=Spring 容器初始化后）
        validation = "true",        // 启用参数验证（需 JSR 303 注解）
        cache = "lru",              // 结果缓存：lru / threadlocal / jcache
        mock = "force:return null", // 服务降级 Mock
        stub = "com.example.UserServiceStub",  // 本地存根
        token = "true",             // 令牌验证（自动生成）
        tag = "gray",               // 标签路由（Dubbo 3）
        protocol = "dubbo"          // 指定协议
)
@Component
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Override
    public User getUserById(Long id) {
        log.info(">>> [Provider] getUserById({})", id);
        User user = new User(id, "用户" + id, "user" + id + "@example.com");
        log.info("<<< [Provider] 返回: {}", user);
        return user;
    }
}
