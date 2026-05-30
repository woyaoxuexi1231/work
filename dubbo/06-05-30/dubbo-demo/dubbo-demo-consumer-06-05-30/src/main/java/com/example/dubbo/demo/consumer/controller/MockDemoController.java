package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务降级演示控制器。
 *
 * <p><b>三种调用模式对比：</b></p>
 * <pre>
 * GET /mock/normal/1    — 无降级，Provider 挂了直接抛 500
 * GET /mock/force/1     — 彻底降级，完全不发 RPC，直接返回 null
 * GET /mock/fail/1      — 被动降级，先发 RPC，失败才返回 null
 * GET /mock/class/1     — Mock 类降级，返回自定义默认对象
 * </pre>
 */
@RestController
public class MockDemoController {

    /** 无降级 — Provider 挂了就抛异常 */
    @DubboReference(version = "1.0.0", group = "demo", check = false, timeout = 2000)
    private UserService noMockService;

    /** force 降级 — Provider 挂了也不发 RPC，直接返回 null */
    @DubboReference(version = "1.0.0", group = "demo", check = false, timeout = 2000, mock = "force:return null")
    private UserService forceMockService;

    /** fail 降级 — 先尝试 RPC，失败才返回 null */
    @DubboReference(version = "1.0.0", group = "demo", check = false, timeout = 200, mock = "fail:return null")
    private UserService failMockService;

    /** Mock 类降级 — 返回自定义默认对象 */
    @DubboReference(version = "1.0.0", group = "demo", check = false, timeout = 2000,
            mock = "com.example.dubbo.demo.api.mock.UserServiceMock")
    private UserService classMockService;

    // ═══════════════════════════════════════════
    // ① 正常调用（无降级）
    // ═══════════════════════════════════════════
    @GetMapping("/mock/normal/{id}")
    public Object testNormal(@PathVariable Long id) {
        try {
            User user = noMockService.getUserById(id);
            Map<String, Object> m = new HashMap<>();
            m.put("mode", "正常调用");
            m.put("user", user);
            return m;
        } catch (Exception e) {
            Map<String, Object> m = new HashMap<>();
            m.put("mode", "正常调用");
            m.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return m;
        }
    }

    // ═══════════════════════════════════════════
    // ② force 降级 — 彻底降级，不走 RPC
    // ═══════════════════════════════════════════
    @GetMapping("/mock/force/{id}")
    public Map<String, Object> testForce(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        User user = forceMockService.getUserById(id);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> m = new HashMap<>();
        m.put("mode", "force 降级（彻底）");
        m.put("user", user);
        m.put("说明", "不发起 RPC，直接返回 null。0ms 说明没走网络");
        m.put("耗时", elapsed + "ms");
        return m;
    }

    // ═══════════════════════════════════════════
    // ③ fail 降级 — 先发 RPC，失败才降级
    // ═══════════════════════════════════════════
    @GetMapping("/mock/fail/{id}")
    public Map<String, Object> testFail(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        User user = failMockService.getUserById(id);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> m = new HashMap<>();
        m.put("mode", "fail 降级（被动）");
        m.put("user", user);
        m.put("说明", "先尝试 RPC → 超时/失败 → 返回 null。耗时=等待超时的时间");
        m.put("耗时", elapsed + "ms");
        return m;
    }

    // ═══════════════════════════════════════════
    // ④ Mock 类降级 — 返回自定义默认用户
    // ═══════════════════════════════════════════
    @GetMapping("/mock/class/{id}")
    public Map<String, Object> testClassMock(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        User user = classMockService.getUserById(id);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> m = new HashMap<>();
        m.put("mode", "Mock 类降级（自定义返回值）");
        m.put("user", user);
        m.put("说明", "返回 UserServiceMock 中定义的默认用户对象，而非 null");
        m.put("耗时", elapsed + "ms");
        return m;
    }
}
