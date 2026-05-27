package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.Order;
import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.OrderService;
import com.example.dubbo.demo.api.service.UserService;

import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h3>用户服务 HTTP 控制器 — Consumer 入口</h3>
 *
 * <p>
 * 本 Controller 作为 <b>Dubbo Consumer 的对外 HTTP 门面</b>，
 * 将 HTTP 请求转换为 Dubbo RPC 调用，向最终用户（浏览器 / Postman）暴露 REST API。
 * </p>
 *
 * <h4>架构层次</h4>
 * <pre>
 * 浏览器 / curl
 *      │  HTTP GET/POST
 *      ▼
 * ┌─────────────────┐
 * │ UserController  │  ← 本类（Spring MVC Controller）
 * │   ↓              │
 * │ @DubboReference  │  ← RPC 代理（动态代理对象）
 * │   ↓              │
 * │ Dubbo Framework  │  ← 负载均衡 / 序列化 / 网络传输
 * └─────────────────┘
 *      │  TCP (Dubbo 协议, 端口 20880)
 *      ▼
 * ┌─────────────────┐
 * │ Provider 服务端  │
 * │ UserServiceImpl  │
 * └─────────────────┘
 * </pre>
 *
 * <h4>@DubboReference 注解说明</h4>
 * <table border="1">
 *   <tr><th>属性</th><th>默认值</th><th>说明</th></tr>
 *   <tr><td>{@code version}</td><td>""（空）</td><td>必须与 Provider 的 version 匹配</td></tr>
 *   <tr><td>{@code group}</td><td>""（空）</td><td>必须与 Provider 的 group 匹配</td></tr>
 *   <tr><td>{@code timeout}</td><td>1000ms</td><td>调用超时（可被 Provider 端配置覆盖）</td></tr>
 *   <tr><td>{@code retries}</td><td>2</td><td>失败重试次数</td></tr>
 *   <tr><td>{@code check}</td><td>true</td><td>启动时是否检查 Provider 可用</td></tr>
 *   <tr><td>{@code loadbalance}</td><td>random</td><td>负载均衡策略（random / roundrobin / leastactive / consistenthash）</td></tr>
 *   <tr><td>{@code mock}</td><td>""（空）</td><td>服务降级 Mock 类名</td></tr>
 * </table>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see UserService
 * @see OrderService
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    // ================================================================
    // @DubboReference — Dubbo 服务引用注解
    //
    // 此注解告诉 Dubbo：在 Spring 容器中注入一个远程代理对象。
    // 当调用 userService.getUserById(1L) 时，实际上会触发一次
    // 完整的 RPC 调用链路。
    //
    // check = false：启动时不检查 Provider 是否可用
    //   （设为 false 是为了演示"Consumer 先于 Provider 启动"的场景，
    //     实际生产环境建议设置为 true 以快速发现问题）
    // ================================================================

    /** 用户服务远程代理 */
    @DubboReference(
            version = "1.0.0",          // 匹配 Provider 的版本号
            group = "demo",             // 匹配 Provider 的分组
            timeout = 5000,             // Consumer 端超时（毫秒）
            retries = 1,                // 失败后重试 1 次
            check = false,              // 启动时不检查 Provider 是否可用
            loadbalance = "random"      // 负载均衡：随机+权重
    )
    private UserService userService;

    /** 订单服务远程代理 */
    @DubboReference(
            version = "1.0.0",
            group = "demo",
            timeout = 5000,
            retries = 1,
            check = false,
            loadbalance = "roundrobin"   // 负载均衡：轮询（演示不同策略）
    )
    private OrderService orderService;

    // ================================================================
    // REST API 端点
    // ================================================================

    /**
     * 查询用户详情。
     *
     * <p>示例：{@code GET /api/users/1}</p>
     *
     * @param id 用户 ID
     * @return 用户信息；不存在返回 404 提示
     */
    @GetMapping("/{id}")
    public Map<String, Object> getUserById(@PathVariable Long id) {
        log.info(">>> [Consumer Controller] HTTP 请求: GET /api/users/{}", id);

        // ---- 隐式传参：通过 RpcContext 传递 Consumer 应用名 ----
        RpcContext.getContext().setAttachment("consumerApp", "dubbo-demo-consumer");

        // ---- Dubbo RPC 调用（同步阻塞） ----
        // 这一行代码背后触发：负载均衡 → 序列化 → Netty 发送 → 等待响应 → 反序列化
        User user = userService.getUserById(id);

        Map<String, Object> result = new HashMap<>();
        if (user != null) {
            result.put("success", true);
            result.put("data", user);
            log.info("<<< [Consumer Controller] 返回: {}", user);
        } else {
            result.put("success", false);
            result.put("message", "用户不存在: id=" + id);
            log.warn("<<< [Consumer Controller] 用户不存在: id={}", id);
        }

        return result;
    }

    /**
     * 查询所有用户。
     *
     * <p>示例：{@code GET /api/users}</p>
     *
     * @return 用户列表
     */
    @GetMapping
    public Map<String, Object> listAllUsers() {
        log.info(">>> [Consumer Controller] HTTP 请求: GET /api/users");

        // Dubbo RPC 调用
        List<User> users = userService.listAllUsers();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", users.size());
        result.put("data", users);

        log.info("<<< [Consumer Controller] 返回 {} 条用户", users.size());
        return result;
    }

    /**
     * 创建新用户。
     *
     * <p>示例：{@code POST /api/users}，请求体 JSON：
     * <pre>
     * { "username": "赵六", "email": "zhaoliu@example.com", "age": 30 }
     * </pre>
     * </p>
     *
     * @param user 新用户信息（不含 ID）
     * @return 创建结果（含生成的 ID）
     */
    @PostMapping
    public Map<String, Object> createUser(@RequestBody User user) {
        log.info(">>> [Consumer Controller] HTTP 请求: POST /api/users, body={}", user);

        // Dubbo RPC 调用 — 写操作
        Long newId = userService.createUser(user);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("newUserId", newId);
        result.put("message", "用户创建成功");

        log.info("<<< [Consumer Controller] 用户创建成功: id={}", newId);
        return result;
    }

    /**
     * 删除用户。
     *
     * <p>示例：{@code DELETE /api/users/1}</p>
     *
     * @param id 用户 ID
     * @return 删除结果
     */
    @GetMapping("/delete/{id}")  // 使用 GET 便于浏览器测试
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        log.info(">>> [Consumer Controller] HTTP 请求: GET /api/users/delete/{}", id);

        boolean deleted = userService.deleteUser(id);

        Map<String, Object> result = new HashMap<>();
        result.put("success", deleted);
        result.put("message", deleted ? "删除成功" : "用户不存在");

        log.info("<<< [Consumer Controller] 删除结果: {}", deleted);
        return result;
    }

    /**
     * 查询用户的订单列表。
     *
     * <p>演示 Consumer 调用 <b>第二个服务</b>（OrderService）。</p>
     * <p>示例：{@code GET /api/users/1/orders}</p>
     *
     * @param userId 用户 ID
     * @return 该用户的订单列表
     */
    @GetMapping("/{userId}/orders")
    public Map<String, Object> getOrdersByUserId(@PathVariable Long userId) {
        log.info(">>> [Consumer Controller] HTTP 请求: GET /api/users/{}/orders", userId);

        // 隐式传参
        RpcContext.getContext().setAttachment("consumerApp", "dubbo-demo-consumer");

        // Dubbo RPC 调用 — 跨服务调用 OrderService
        List<Order> orders = orderService.listOrdersByUserId(userId);

        // 聚合查询：同时获取用户信息和订单
        User user = userService.getUserById(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("user", user);
        result.put("orderCount", orders.size());
        result.put("orders", orders);

        log.info("<<< [Consumer Controller] 用户 {} 有 {} 笔订单", userId, orders.size());
        return result;
    }

    /**
     * 查询用户的订单总额。
     *
     * <p>演示 Dubbo 对 {@link BigDecimal} 类型的序列化支持。</p>
     * <p>示例：{@code GET /api/users/1/orders/total}</p>
     *
     * @param userId 用户 ID
     * @return 订单总额
     */
    @GetMapping("/{userId}/orders/total")
    public Map<String, Object> getOrderTotal(@PathVariable Long userId) {
        log.info(">>> [Consumer Controller] HTTP 请求: GET /api/users/{}/orders/total", userId);

        // Dubbo RPC 调用（BigDecimal 序列化）
        BigDecimal total = orderService.getTotalAmountByUserId(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("userId", userId);
        result.put("totalAmount", total);

        log.info("<<< [Consumer Controller] 用户 {} 订单总额: {}", userId, total);
        return result;
    }
}
