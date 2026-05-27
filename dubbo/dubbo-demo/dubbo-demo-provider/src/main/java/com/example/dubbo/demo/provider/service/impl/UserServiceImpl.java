package com.example.dubbo.demo.provider.service.impl;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;

import org.apache.dubbo.config.annotation.DubboService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <h3>用户服务实现 — Dubbo Provider 核心</h3>
 *
 * <p>
 * 本类是 {@link UserService} 接口的具体实现，标记为 Dubbo 服务暴露。
 * </p>
 *
 * <h4>关键注解说明</h4>
 * <table border="1">
 *   <tr><th>注解</th><th>作用</th></tr>
 *   <tr>
 *     <td>{@code @DubboService}</td>
 *     <td>
 *       Dubbo 2.7+ 的服务暴露注解（替代旧版 {@code @Service}）。<br>
 *       标记此注解后，Dubbo 会在 Spring 容器启动时自动：<br>
 *       <ol>
 *         <li>将该 Bean 封装为 {@code ServiceConfig}</li>
 *         <li>启动协议端口（如 dubbo://:20880）</li>
 *         <li>向注册中心注册服务地址</li>
 *       </ol>
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@code version = "1.0.0"}</td>
 *     <td>
 *       服务版本号 — 支持灰度发布。<br>
 *       Consumer 可指定版本引用，实现"先升级部分 Provider，Consumer 逐步切换"。
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@code group = "demo"}</td>
 *     <td>
 *       服务分组 — 同一接口可按用途隔离（如 dev / test / prod）。
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>{@code timeout = 3000}</td>
 *     <td>方法调用超时（毫秒）— 防止 Consumer 无限等待。</td>
 *   </tr>
 *   <tr>
 *     <td>{@code retries = 2}</td>
 *     <td>失败重试次数（不含首次调用）。</td>
 *   </tr>
 * </table>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see UserService
 * @see org.apache.dubbo.config.annotation.DubboService
 */
@DubboService(
        version = "1.0.0",       // 服务版本
        group = "demo",          // 服务分组
        timeout = 3000,          // 超时 3 秒
        retries = 2,             // 失败后重试 2 次
        weight = 100             // 权重（负载均衡使用）
)
@Component
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 模拟内存数据库 */
    private final Map<Long, User> userStore = new ConcurrentHashMap<>();

    /** ID 自增生成器 */
    private final AtomicLong idGenerator = new AtomicLong(0);

    // ==================== 初始化模拟数据 ====================
    {
        userStore.put(1L, new User(1L, "张三", "zhangsan@example.com", 28));
        userStore.put(2L, new User(2L, "李四", "lisi@example.com", 35));
        userStore.put(3L, new User(3L, "王五", "wangwu@example.com", 22));
        idGenerator.set(3L);  // ID 从 4 开始分配
        log.info("UserServiceImpl 初始化完成，已加载 {} 条模拟数据", userStore.size());
    }

    // ==================== 接口实现 ====================

    /**
     * {@inheritDoc}
     *
     * <p>RPC 调用链路中的 Provider 端执行此方法。</p>
     */
    @Override
    public User getUserById(Long id) {
        log.info(">>> [Provider] 收到 RPC 请求: getUserById({})", id);

        // 模拟数据库查询
        User user = userStore.get(id);

        if (user != null) {
            log.info("<<< [Provider] 返回结果: {}", user);
        } else {
            log.warn("<<< [Provider] 用户不存在: id={}", id);
        }

        return user;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<User> listAllUsers() {
        log.info(">>> [Provider] 收到 RPC 请求: listAllUsers()");

        List<User> users = new ArrayList<>(userStore.values());

        log.info("<<< [Provider] 返回 {} 条用户记录", users.size());
        return users;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * 演示写操作的 RPC 调用：Consumer 传入 User 对象，
     * Provider 执行创建逻辑并返回新 ID。
     * </p>
     */
    @Override
    public Long createUser(User user) {
        log.info(">>> [Provider] 收到 RPC 请求: createUser({})", user);

        // 生成新 ID
        long newId = idGenerator.incrementAndGet();
        user.setId(newId);

        // 存入"数据库"
        userStore.put(newId, user);

        log.info("<<< [Provider] 用户创建成功: id={}, username={}", newId, user.getUsername());
        return newId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteUser(Long id) {
        log.info(">>> [Provider] 收到 RPC 请求: deleteUser({})", id);

        User removed = userStore.remove(id);

        boolean success = removed != null;
        log.info("<<< [Provider] 删除结果: {} (removed={})", success, removed);
        return success;
    }
}
