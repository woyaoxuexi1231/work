package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发与连接控制演示。
 *
 * <p><b>三个核心参数：</b></p>
 * <table border="1">
 *   <tr><th>参数</th><th>端</th><th>作用</th><th>示例</th></tr>
 *   <tr><td>{@code executes}</td><td>Provider</td><td>服务端最大并发执行数，超限请求排队或抛异常</td><td>executes=2，第 3 个并发请求被拒绝</td></tr>
 *   <tr><td>{@code actives}</td><td>Consumer</td><td>每方法最大并发数，超限阻塞等待</td><td>actives=2，第 3 个并发请求排队等</td></tr>
 *   <tr><td>{@code connections}</td><td>两端</td><td>每 Consumer→Provider 的连接数</td><td>dubbo 协议长连接，1 就够</td></tr>
 * </table>
 *
 * <p><b>测试准备：</b>在 Provider 的 {@code getUserById} 中加 1 秒延迟（{@code Thread.sleep(1000)}），
 * 否则并发请求太快，还没触发限流就全跑完了。</p>
 */
@RestController
public class ConcurrencyController {

    // ════════════════════════════════════════════════════════════
    // 无限制 — 对比基准
    // ════════════════════════════════════════════════════════════
    @DubboReference(version = "1.0.0", group = "demo", check = false, timeout = 10000)
    private UserService unlimitedService;

    // ════════════════════════════════════════════════════════════
    // actives=2 — Consumer 端限制：最多 2 个并发
    // ════════════════════════════════════════════════════════════
    @DubboReference(version = "1.0.0", group = "demo", check = false, timeout = 10000, actives = 2)
    private UserService activeLimitService;

    // ════════════════════════════════════════════════════════════
    // ① 无限制 — 同时发 5 个请求，全部并行
    // ════════════════════════════════════════════════════════════
    @GetMapping("/concurrency/unlimited")
    public Map<String, Object> testUnlimited() throws Exception {
        int count = 5;
        long start = System.currentTimeMillis();

        // 同时发 5 个请求
        CompletableFuture<?>[] futures = new CompletableFuture[count];
        for (int i = 0; i < count; i++) {
            int finalI = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                unlimitedService.getUserById((long) finalI + 1);
            });
        }
        CompletableFuture.allOf(futures).join();

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> m = new HashMap<>();
        m.put("模式", "无限制");
        m.put("并发数", count);
        m.put("总耗时", elapsed + "ms");
        m.put("结论", "5 个请求全部并行执行，耗时 ≈ 单个请求耗时");
        return m;
    }

    // ════════════════════════════════════════════════════════════
    // ② actives=2 — Consumer 端限制每次最多 2 个并发
    // ════════════════════════════════════════════════════════════
    @GetMapping("/concurrency/actives")
    public Map<String, Object> testActives() throws Exception {
        int count = 5;
        long start = System.currentTimeMillis();

        CompletableFuture<?>[] futures = new CompletableFuture[count];
        for (int i = 0; i < count; i++) {
            int finalI = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                activeLimitService.getUserById((long) finalI + 1);
            });
        }
        CompletableFuture.allOf(futures).join();

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> m = new HashMap<>();
        m.put("模式", "actives=2（Consumer 限流）");
        m.put("并发数", count);
        m.put("总耗时", elapsed + "ms");
        m.put("结论",
                "actives=2 限制同一时刻最多 2 个请求在处理，其余排队等待。"
                        + "因此 5 个请求分为 3 批（2+2+1），耗时 ≈ 3 倍单个请求耗时");
        return m;
    }

    // ════════════════════════════════════════════════════════════
    // ③ 参数说明
    // ════════════════════════════════════════════════════════════
    @GetMapping("/concurrency")
    public Map<String, Object> help() {
        Map<String, Object> m = new HashMap<>();
        m.put("说明", "Dubbo 并发与连接控制参数演示");
        m.put("测试前", "在 Provider UserServiceImpl.getUserById() 中添加 Thread.sleep(1000) 模拟慢接口");
        m.put("端点", "GET /concurrency/unlimited  — 无限制并发（5 请求并行）");
        m.put("端点", "GET /concurrency/actives    — actives=2 限流（5 请求分 3 批执行）");
        m.put("总结", new String[]{
                "actives — Consumer 端限制，控制单个 Consumer 对某方法的并发数",
                "executes — Provider 端限制，控制所有 Consumer 对某方法的总并发数",
                "connections — 连接数，dubbo 协议长连接设 1 就够了",
                "场景: 突然的促销流量，用 actives 防止突发请求打满 Provider 线程池"
        });
        return m;
    }
}
