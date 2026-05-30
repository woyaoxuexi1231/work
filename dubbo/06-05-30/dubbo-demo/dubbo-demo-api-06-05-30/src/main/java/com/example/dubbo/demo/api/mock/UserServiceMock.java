package com.example.dubbo.demo.api.mock;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * 服务降级 Mock 类 — Provider 不可用或调用失败时，Consumer 端执行兜底逻辑。
 *
 * <p><b>调用时机：</b></p>
 * <table border="1">
 *   <tr><th>mock 值</th><th>行为</th></tr>
 *   <tr><td>{@code "force:return null"}</td><td><b>不发起 RPC</b>，直接返回 null（彻底降级，省一次网络开销）</td></tr>
 *   <tr><td>{@code "fail:return null"}</td><td><b>先尝试 RPC</b>，失败才返回 null（被动降级）</td></tr>
 *   <tr><td>{@code "force:throw java.lang.RuntimeException"}</td><td>不发起 RPC，直接抛异常</td></tr>
 *   <tr><td>{@code "fail:return false"}</td><td>RPC 失败时返回 false</td></tr>
 *   <tr><td>{@code "com.example.XxxMock"}</td><td>指定 Mock 类，可返回自定义默认值（本文件演示）</td></tr>
 * </table>
 *
 * <p><b>与 Stub 的区别：</b></p>
 * <ul>
 *   <li>Stub — RPC <b>之前</b>执行，参数有效才发 RPC。属于"主动拦截"。</li>
 *   <li>Mock — RPC <b>失败后</b>执行，Provider 挂了才兜底。属于"被动降级"。</li>
 *   <li>同时存在时调用链：Controller → Stub → RPC 代理 → Provider（失败）→ Mock</li>
 * </ul>
 *
 * <p><b>约束：</b>Mock 类必须有一个<b>无参构造方法</b>（Dubbo 通过反射创建实例）。</p>
 */
public class UserServiceMock implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceMock.class);

    /**
     * 返回默认用户 — 代替真实的远程调用结果。
     *
     * <p>当 Provider 不可用或调用超时/重试耗尽时，Dubbo 调用此方法返回兜底值，
     * 而不是抛异常给 Controller。</p>
     */
    @Override
    public User getUserById(Long id) {
        log.warn("【Mock】getUserById({}) — Provider 不可用，返回降级默认用户", id);
        return new User(id, "降级用户-" + id, "fallback@mock.com");
    }

    @Override
    public CompletableFuture<User> getUserByIdAsync(Long id) {
        log.warn("【Mock】getUserByIdAsync({}) — 异步降级", id);
        return CompletableFuture.completedFuture(new User(id, "降级用户-" + id, "fallback@mock.com"));
    }
}
