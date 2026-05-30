package com.example.dubbo.demo.api.stub;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地存根 — 在 Consumer 端运行的本地代理。
 *
 * <p>调用链路：Controller → <b>Stub（本地）</b> → RPC 代理 → Provider</p>
 *
 * <p><b>存根能做什么：</b></p>
 * <ul>
 *   <li><b>参数校验</b> — id 为空或负数时直接返回默认值，不发起 RPC（省一次网络开销）</li>
 *   <li><b>本地缓存命中</b> — 同一个 id 短时间内重复查，缓存返回，不走 RPC</li>
 *   <li><b>结果增强</b> — Provider 返回 null 时，stub 可以返回一个默认对象而非 null</li>
 *   <li><b>调用前日志</b> — 记录每次调用的参数、耗时</li>
 * </ul>
 *
 * <p><b>约束：</b></p>
 * <ul>
 *   <li>必须实现与远程服务相同的接口（{@link UserService}）</li>
 *   <li>必须有一个构造方法，参数是远程接口类型（Dubbo 注入 RPC 代理进来）</li>
 *   <li>必须是一个 public 类，且有一个无参构造，或者一个接受远程接口的构造</li>
 *   <li>在 {@code @DubboReference(stub="...")} 中指定存根的全限定类名</li>
 * </ul>
 */
public class UserServiceStub implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceStub.class);

    /** Dubbo 注入的远程 RPC 代理（真正的网络调用对象） */
    private final UserService remoteProxy;

    /**
     * Dubbo 要求的构造方法——框架把 RPC 代理对象传进来。
     *
     * @param remoteProxy 远程调用代理
     */
    public UserServiceStub(UserService remoteProxy) {
        this.remoteProxy = remoteProxy;
    }

    /**
     * 带 Stub 的用户查询。
     *
     * <p>Stub 做了三件事：</p>
     * <ol>
     *   <li>参数校验：id 无效时直接返回默认值，<b>不发起 RPC</b></li>
     *   <li>调用前日志：输出参数信息</li>
     *   <li>调用远程方法并处理返回结果</li>
     * </ol>
     */
    @Override
    public User getUserById(Long id) {
        log.info("【Stub】getUserById({}) — 参数校验开始", id);

        // ── ① 参数校验：无效参数直接返回，不走 RPC ──
        if (id == null || id <= 0) {
            log.warn("【Stub】参数无效 id={}，直接返回默认值，未发起 RPC", id);
            return new User(0L, "默认用户", "default@example.com");
        }

        // ── ② 调用前日志 ──
        log.info("【Stub】参数校验通过，转发给远程 RPC 代理");

        // ── ③ 调用真正的远程方法 ──
        long start = System.currentTimeMillis();
        User result = remoteProxy.getUserById(id);
        long elapsed = System.currentTimeMillis() - start;

        // ── ④ 结果处理：Provider 返回 null 时给个默认值 ──
        if (result == null) {
            log.warn("【Stub】Provider 返回 null，由 Stub 提供默认值");
            result = new User(id, "用户" + id, "user" + id + "@example.com");
        }

        log.info("【Stub】远程调用完成，耗时={}ms，结果={}", elapsed, result);
        return result;
    }

    /**
     * 异步方法暂不演示 Stub，直接透传远程调用。
     */
    @Override
    public java.util.concurrent.CompletableFuture<User> getUserByIdAsync(Long id) {
        return remoteProxy.getUserByIdAsync(id);
    }
}
