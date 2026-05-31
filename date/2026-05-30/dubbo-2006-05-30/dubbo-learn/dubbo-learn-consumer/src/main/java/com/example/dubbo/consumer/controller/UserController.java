package com.example.dubbo.consumer.controller;

import com.example.dubbo.api.UserService;
import com.example.dubbo.api.model.ComplexType;
import com.example.dubbo.api.model.User;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消费者 HTTP 入口 — 通过 Web 触发 Dubbo RPC。
 *
 * @DubboReference 参数说明：
 *   version = "1.0.0"  → 必须和提供者一致
 *   timeout = 2000      → 消费者超时 2s（优先于提供者的 3s）
 *   retries = 2         → 失败重试 2 次，共 3 次调用
 *   cluster = "failover"→ 失败后切换其他节点
 *   mock    = "true"    → 启用 Mock，失败走 UserServiceMock
 *   check   = false     → 启动不检查提供者是否存在
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @DubboReference(
        version = "1.0.0",
        timeout = 2000,
        retries = 2,
        cluster = "failover",
        mock = "true",
        check = false
    )
    private UserService userService;

    /** GET /user/1 */
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    /** GET /user/all */
    @GetMapping("/all")
    public List<User> listAll() {
        return userService.listAll();
    }

    /**
     * GET /user/complex
     *
     * 验证 Hessian2 泛型是否丢失。
     * 控制台会输出第一个元素的真实类型：
     *   ✅ User → 泛型完整
     *   ❌ HashMap → 泛型丢失（Hessian2 的坑）
     */
    @GetMapping("/complex")
    public ComplexType getComplexData() {
        ComplexType data = userService.getComplexData();

        if (data.getDepartmentUsers() != null && !data.getDepartmentUsers().isEmpty()) {
            List<User> users = data.getDepartmentUsers().values().iterator().next();
            if (!users.isEmpty()) {
                Object first = users.get(0);
                System.out.println("[Consumer] departmentUsers 元素类型: " + first.getClass().getName());
                System.out.println(first instanceof User
                        ? "[Consumer] ✅ 泛型正常，是 User"
                        : "[Consumer] ❌ 泛型丢失！是 " + first.getClass().getSimpleName() + " 而非 User");
            }
        }

        if (data.getDepartments() != null && !data.getDepartments().isEmpty()) {
            System.out.println("[Consumer] departments[0].members[0] 类型: "
                    + data.getDepartments().get(0).getMembers().get(0).getClass().getName());
            System.out.println("[Consumer] ✅ 具体 POJO 序列化始终正常");
        }

        return data;
    }

    /**
     * GET /user/slow/{id}/{ms}
     *
     * 超时验证：
     *   ms=500  → 正常返回
     *   ms=3000 → 消费者 2s 超时 → Failover 重试 2 次 → 全部超时 → Mock 降级
     */
    @GetMapping("/slow/{id}/{ms}")
    public User slowQuery(@PathVariable Long id, @PathVariable long ms) {
        System.out.println("[Consumer] slowQuery(" + id + ", " + ms + "ms)"
                + " — 消费者超时=2000ms" + (ms > 2000 ? " ⚠️ 将触发超时→重试→Mock" : ""));
        return userService.slowQuery(id, ms);
    }
}
