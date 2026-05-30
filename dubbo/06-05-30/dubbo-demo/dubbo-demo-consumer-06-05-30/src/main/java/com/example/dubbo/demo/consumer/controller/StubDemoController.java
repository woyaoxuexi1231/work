package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import com.example.dubbo.demo.consumer.stub.UserServiceStub;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地存根演示控制器。
 *
 * <p><b>调用链路：</b></p>
 * <pre>
 * 浏览器 GET /stub/1
 *    │
 *    ▼
 * UserServiceStub.getUserById(1)    ← 在 Consumer 进程内执行
 *    │
 *    ├─ id 无效？→ 直接返回默认值，不发起 RPC
 *    ├─ id 有效？→ 转发给远程 RPC 代理
 *    │
 *    ▼
 * UserServiceImpl.getUserById(1)    ← 在 Provider 进程内执行
 * </pre>
 *
 * <p><b>和不用 Stub 的区别：</b></p>
 * <table border="1">
 *   <tr><th></th><th>无 Stub</th><th>有 Stub</th></tr>
 *   <tr><td>GET /user/-1</td><td>发 RPC → Provider 返回 null/null 指针风险</td><td>Stub 拦截，直接返回默认用户，零网络开销</td></tr>
 *   <tr><td>GET /user/1</td><td>直接发 RPC</td><td>Stub 先校验参数 → 通过后才发 RPC</td></tr>
 * </table>
 */
@RestController
public class StubDemoController {

    /**
     * 带本地存根的 Dubbo 引用。
     *
     * <p>{@code stub = "com.example.dubbo.demo.consumer.stub.UserServiceStub"}
     * 告诉 Dubbo：在生成 RPC 代理之前，先用 UserServiceStub 包装一层。
     * Consumer 端实际注入的 Bean 是 Stub 实例，不是 RPC 代理。
     * Stub 内部持有 remoteProxy 字段，需要远程调用时再调它。</p>
     */
    @DubboReference(
            version = "1.0.0",
            group = "demo",
            check = false,
            stub = "com.example.dubbo.demo.consumer.stub.UserServiceStub"
    )
    private UserService userService;

    /**
     * 测试有效 ID — Stub 校验通过，走 RPC。
     * <pre>
     * GET /stub/1
     * 结果: {"source": "RPC远程调用", "user": User{id=1, username='用户1', ...}}
     * </pre>
     */
    @GetMapping("/stub/{id}")
    public Map<String, Object> testStub(@PathVariable Long id) {
        long start = System.currentTimeMillis();

        // 实际调用的是 Stub.getUserById()，不是远程 proxy
        User user = userService.getUserById(id);

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("耗时", elapsed + "ms");
        result.put("说明", id <= 0
                ? "Stub 拦截：参数无效，直接返回默认值，未发起 RPC"
                : "Stub 放行：参数校验通过，转发给远程 Provider");
        return result;
    }
}
