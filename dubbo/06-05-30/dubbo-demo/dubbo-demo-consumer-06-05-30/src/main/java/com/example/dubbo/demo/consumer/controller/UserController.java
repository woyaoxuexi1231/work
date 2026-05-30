package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class UserController {

    @DubboReference(version = "1.0.0", group = "demo", check = false)
    private UserService userService;

    // ════════════════════════════════════════════════════════════
    // 同步 — 一个一个等
    // ════════════════════════════════════════════════════════════
    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    // ════════════════════════════════════════════════════════════
    // 异步 × 2 — 同时发出，同时等
    // ════════════════════════════════════════════════════════════
    @GetMapping("/user/async/{id}")
    public Map<String, Object> getUserAsync(@PathVariable Long id) throws Exception {

        long start = System.currentTimeMillis();

        // ① 同时发出两个请求（非阻塞，瞬间返回 Future）
        CompletableFuture<User> f1 = userService.getUserByIdAsync(id);
        CompletableFuture<User> f2 = userService.getUserByIdAsync(999L);

        // ② 两个请求在 Provider 端并行执行
        //    此时 CPU 不空等，可以去干别的事

        // ③ 一起等结果（只等最慢的那个）
        User user1 = f1.get();
        User user2 = f2.get();

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("user1", user1);
        result.put("user2", user2);
        result.put("耗时", elapsed + "ms");
        result.put("结论", "并行 → 总耗时 ≈ 单个请求耗时");
        return result;
    }

    // ════════════════════════════════════════════════════════════
    // 同步 × 2 — 对比：串行等
    // ════════════════════════════════════════════════════════════
    @GetMapping("/user/sync-two/{id}")
    public Map<String, Object> getUserSyncTwo(@PathVariable Long id) {

        long start = System.currentTimeMillis();

        User user1 = userService.getUserById(id);    // 先等第 1 个回来
        User user2 = userService.getUserById(999L);  // 再等第 2 个回来

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("user1", user1);
        result.put("user2", user2);
        result.put("耗时", elapsed + "ms");
        result.put("结论", "串行 → 总耗时 ≈ 两倍单个请求耗时");
        return result;
    }
}
