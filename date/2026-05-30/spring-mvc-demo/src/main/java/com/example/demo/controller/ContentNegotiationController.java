package com.example.demo.controller;

import com.example.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 内容协商演示 —— HttpMessageConverter 根据 Accept 头选择响应格式
 *
 * <h3>什么是内容协商（Content Negotiation）？</h3>
 * <p>一个 URL，多种格式。客户端通过 {@code Accept} 请求头告诉服务端"我想要什么格式"，
 * 服务端选择合适的 {@code HttpMessageConverter} 序列化响应。</p>
 *
 * <h3>测试方法</h3>
 * <pre>
 *   # 请求 JSON（默认）
 *   curl -H "Accept: application/json" http://localhost:8080/negotiation/users
 *
 *   # 请求 CSV（由自定义 CsvHttpMessageConverter 处理）👈 关键演示！
 *   curl -H "Accept: text/csv" http://localhost:8080/negotiation/users
 *
 *   # 不指定 Accept（返回默认 JSON）
 *   curl http://localhost:8080/negotiation/users
 * </pre>
 *
 * <h3>内部流程</h3>
 * <pre>
 *   Controller 返回 List&lt;User&gt;
 *       │
 *       ▼
 *   RequestResponseBodyMethodProcessor.writeWithMessageConverters()
 *       │
 *       ├─ 遍历所有 HttpMessageConverter：
 *       │    ├─ MappingJackson2HttpMessageConverter.canWrite(..., "application/json") → true ✅
 *       │    ├─ CsvHttpMessageConverter.canWrite(..., "application/json") → false ❌
 *       │    └─ ...
 *       │
 *       ├─ 若 Accept: text/csv：
 *       │    ├─ MappingJackson2HttpMessageConverter.canWrite(..., "text/csv") → false ❌
 *       │    └─ CsvHttpMessageConverter.canWrite(..., "text/csv") → true ✅ → 输出 CSV！
 *       │
 *       ▼
 *   HTTP 响应（JSON 或 CSV）
 * </pre>
 */
@RestController
@RequestMapping("/negotiation")
public class ContentNegotiationController {

    private static final Logger log = LoggerFactory.getLogger(ContentNegotiationController.class);

    /**
     * 同一个接口，根据 Accept 头返回 JSON 或 CSV。
     *
     * <p>不需要在方法上声明 produces！
     * Spring 自动根据 Accept 头 + 已注册的 Converter 判断。
     */
    @GetMapping("/users")
    public List<User> listUsers() {
        log.info("📥 内容协商演示：返回用户列表");

        return Arrays.asList(
                new User(1L, "张三", "zhangsan@example.com"),
                new User(2L, "李四", "lisi@example.com"),
                new User(3L, "王五", "wangwu@example.com")
        );
    }

    /**
     * 强制指定 produces = "application/json"，即使 Accept 是 text/csv 也返回 JSON。
     *
     * <p>这样客户端 Accept 头不生效 —— 因为 produces 限制了可用 MediaType。
     */
    @GetMapping(value = "/users-json-only", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<User> listUsersJsonOnly() {
        log.info("📥 强制 JSON（produces 限制）");
        return listUsers();
    }
}
