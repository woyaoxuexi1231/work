package com.example.demo.controller;

import com.example.demo.annotation.CurrentUser;
import com.example.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 用户接口 —— 演示 MVC 核心流程 & 自定义参数解析器
 *
 * <h3>一个请求的完整 MVC 流程（结合日志观察）</h3>
 * <pre>
 *   客户端请求  GET /users?keyword=zhang
 *       │
 *       ▼
 *  ① DispatcherServlet.doDispatch()
 *       │
 *       ├─ getHandler() → HandlerMapping 根据 URL "/users" 找到本类的 listUsers() 方法
 *       │    [日志] RequestMappingHandlerMapping: Mapped "{[/users],methods=[GET]}"
 *       │
 *       ├─ getHandlerAdapter() → 判断 Controller 类型，选择 RequestMappingHandlerAdapter
 *       │
 *       ├─ ha.handle() → HandlerAdapter 调用：
 *       │    ├─ ③ resolveArgument() → 参数解析器链
 *       │    │    ├─ RequestParamMethodArgumentResolver   处理 @RequestParam("keyword")
 *       │    │    └─ CurrentUserArgumentResolver          处理 @CurrentUser
 *       │    │
 *       │    ├─ 执行 Controller 方法 → listUsers(...)
 *       │    │
 *       │    └─ ④ handleReturnValue() → 返回值处理器
 *       │         └─ RequestResponseBodyMethodProcessor 遍历 HttpMessageConverter
 *       │              └─ MappingJackson2HttpMessageConverter.write() → 输出 JSON
 *       │
 *       ▼
 *   HTTP 响应 200  [{ "id":1, "name":"张三", ... }, ...]
 * </pre>
 *
 * <p>启动后在控制台日志中搜索这些关键词可以直观看到流程：
 * <ul>
 *   <li>"Mapped {" —— HandlerMapping 注册的路径</li>
 *   <li>"CurrentUserArgumentResolver" —— 自定义解析器是否被调用</li>
 *   <li>"writeWithMessageConverters" —— 选了哪个 Converter</li>
 * </ul>
 */
@RestController         // = @Controller + @ResponseBody（每个方法返回值都自动序列化）
@RequestMapping("/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    // ==================== GET 列表 ====================

    /**
     * 查询用户列表 —— 演示 @RequestParam 参数解析（Spring 内置解析器）
     *
     * <p>参数解析链路：RequestParamMethodArgumentResolver 从 query string 中取出 keyword
     */
    @GetMapping
    public List<User> listUsers(@RequestParam(defaultValue = "") String keyword) {
        log.info("📥 查询用户列表, keyword={}", keyword);

        List<User> users = Arrays.asList(
                new User(1L, "张三", "zhangsan@example.com"),
                new User(2L, "李四", "lisi@example.com"),
                new User(3L, "王五", "wangwu@example.com")
        );

        // @RestController 会自动把 List<User> 通过 Jackson Converter 序列化为 JSON
        return users;
    }

    // ==================== GET 单个用户 ====================

    /**
     * 获取单个用户 —— 演示 @CurrentUser 自定义参数解析器
     *
     * <p>请求时带上 X-User-Id 头即可触发 CurrentUserArgumentResolver。
     * 注意看控制台日志中 "✅ CurrentUserArgumentResolver 解析到用户 ID: xxx"。
     *
     * <p>用法：
     * <pre>
     *   curl -H "X-User-Id: 42" http://localhost:8080/users/profile
     * </pre>
     */
    @GetMapping("/profile")
    public User profile(@CurrentUser User currentUser) {
        log.info("📥 获取当前用户信息: {}", currentUser);
        return currentUser;
    }

    // ==================== POST 创建 ====================

    /**
     * 创建用户 —— 演示 @RequestBody（由 Jackson Converter 反序列化）
     *
     * <p>参数解析链路：
     * <ol>
     *   <li>RequestResponseBodyMethodProcessor 识别到 @RequestBody</li>
     *   <li>读取 Content-Type: application/json</li>
     *   <li>找到 MappingJackson2HttpMessageConverter</li>
     *   <li>调用 readInternal() → JSON → User 对象</li>
     * </ol>
     */
    @PostMapping
    public User createUser(@RequestBody User user, @CurrentUser User operator) {
        log.info("📥 创建用户: {}, 操作人: {}", user, operator);
        // 模拟保存，返回带 ID 的用户
        user.setId(100L);
        return user;
    }
}
