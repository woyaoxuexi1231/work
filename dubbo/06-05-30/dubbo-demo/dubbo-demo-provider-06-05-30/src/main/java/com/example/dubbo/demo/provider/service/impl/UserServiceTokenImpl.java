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
 * 带令牌验证的用户服务实现。
 *
 * <p><b>令牌验证流程：</b></p>
 * <ol>
 *   <li>Provider 设 {@code token = "mySecureToken"} — 只有携带正确 token 的 Consumer 可以调用</li>
 *   <li>Consumer 通过 {@code RpcContext.getContext().setAttachment("token", "mySecureToken")} 携带 token</li>
 *   <li>Provider 收到请求后比对 token — 不匹配则拒绝调用</li>
 * </ol>
 *
 * <p>使用 {@code group = "token-demo"} 与无 token 的普通服务隔离开。</p>
 */
@DubboService(
        version = "1.0.0",
        group = "token-demo",          // 不同分组，与普通服务隔离
        token = "mySecureToken123"     // 固定 token 值（也可设 true 自动生成随机 token）
)
@Component
public class UserServiceTokenImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceTokenImpl.class);

    @Value("${dubbo.protocols.dubbo.port}")
    private String port;

    @Override
    public User getUserById(Long id) {
        log.info(">>> [Token-Provider] getUserById({})", id);
        User user = new User(id, "受保护用户-" + id, "secure" + id + "@example.com");
        log.info("<<< [Token-Provider] 返回: {}", user);
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
