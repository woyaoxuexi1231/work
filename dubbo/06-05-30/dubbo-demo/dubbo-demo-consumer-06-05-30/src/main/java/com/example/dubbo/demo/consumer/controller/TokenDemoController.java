package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 令牌验证（Token）演示。
 *
 * <p><b>令牌验证用来干什么？</b></p>
 * <ul>
 *   <li>防止未经授权的 Consumer 调用 Provider</li>
 *   <li>Provider 端设 {@code token = "xxx"}，Consumer 必须携带匹配的 token 才能调用</li>
 *   <li>多用于内网安全——防止直连 Provider 绕过网关鉴权</li>
 * </ul>
 *
 * <p><b>三种 token 场景演示：</b></p>
 * <table border="1">
 *   <tr><th>端点</th><th>动作</th><th>结果</th></tr>
 *   <tr><td>GET /token/right/1</td><td>携带正确 token 调用</td><td>✅ 正常返回数据</td></tr>
 *   <tr><td>GET /token/wrong/1</td><td>携带错误 token 调用</td><td>❌ Token 不匹配，Provider 拒绝</td></tr>
 *   <tr><td>GET /token/none/1</td><td>不携带 token 调用</td><td>❌ 无 token，Provider 拒绝</td></tr>
 * </table>
 */
@RestController
public class TokenDemoController {

    /** 引用带 token 验证的服务（group = "token-demo"） */
    @DubboReference(
            version = "1.0.0",
            group = "token-demo",
            check = false,
            timeout = 5000
    )
    private UserService tokenService;

    // ════════════════════════════════════════════════════════════
    // ① 正确 token — 通过 RpcContext 传递
    // ════════════════════════════════════════════════════════════
    @GetMapping("/token/right/{id}")
    public Map<String, Object> withRightToken(@PathVariable Long id) {

        // 在调用前通过 RpcContext 设置 token 附件
        // Provider 端收到请求后会比对 token 是否匹配 @DubboService(token = "mySecureToken123")
        RpcContext.getContext().setAttachment("token", "mySecureToken123");

        long start = System.currentTimeMillis();
        User user = tokenService.getUserById(id);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> m = new HashMap<>();
        m.put("结果", "✅ 调用成功");
        m.put("user", user);
        m.put("携带的 token", "mySecureToken123");
        m.put("耗时", elapsed + "ms");
        return m;
    }

    // ════════════════════════════════════════════════════════════
    // ② 错误 token
    // ════════════════════════════════════════════════════════════
    @GetMapping("/token/wrong/{id}")
    public Map<String, Object> withWrongToken(@PathVariable Long id) {

        RpcContext.getContext().setAttachment("token", "wrongToken456");

        long start = System.currentTimeMillis();
        try {
            User user = tokenService.getUserById(id);
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> m = new HashMap<>();
            m.put("结果", "❌ 不应该成功（token 错误应该被拒绝）");
            m.put("user", user);
            m.put("耗时", elapsed + "ms");
            return m;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> m = new HashMap<>();
            m.put("结果", "✅ 按预期被拒绝");
            m.put("错误信息", e.getMessage());
            m.put("携带的 token", "wrongToken456");
            m.put("耗时", elapsed + "ms");
            return m;
        }
    }

    // ════════════════════════════════════════════════════════════
    // ③ 不传 token
    // ════════════════════════════════════════════════════════════
    @GetMapping("/token/none/{id}")
    public Map<String, Object> withoutToken(@PathVariable Long id) {

        // 注意：没有调用 setAttachment("token", ...)

        long start = System.currentTimeMillis();
        try {
            User user = tokenService.getUserById(id);
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> m = new HashMap<>();
            m.put("结果", "❌ 不应该成功（无 token 应该被拒绝）");
            m.put("user", user);
            m.put("耗时", elapsed + "ms");
            return m;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> m = new HashMap<>();
            m.put("结果", "✅ 按预期被拒绝");
            m.put("错误信息", e.getMessage());
            m.put("携带的 token", "无");
            m.put("耗时", elapsed + "ms");
            return m;
        }
    }

    // ════════════════════════════════════════════════════════════
    // ④ 说明
    // ════════════════════════════════════════════════════════════
    @GetMapping("/token")
    public Map<String, Object> help() {
        Map<String, Object> m = new HashMap<>();
        m.put("说明", "令牌验证演示 — Provider 端 token 校验");
        m.put("Provider 配置", "@DubboService(token = \"mySecureToken123\")");
        m.put("Consumer 传参", "RpcContext.getContext().setAttachment(\"token\", \"xxx\")");
        m.put("端点", "GET /token/right/1  — 携带正确 token → ✅ 正常返回");
        m.put("端点", "GET /token/wrong/1  — 携带错误 token → ❌ 被拒绝");
        m.put("端点", "GET /token/none/1   — 不携带 token   → ❌ 被拒绝");
        m.put("使用场景", "内网安全加固：防止未授权的 Consumer 绕过网关直连内部服务");
        return m;
    }
}
