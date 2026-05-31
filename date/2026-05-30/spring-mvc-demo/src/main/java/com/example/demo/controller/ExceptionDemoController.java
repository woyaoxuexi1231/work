package com.example.demo.controller;

import com.example.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异常演示接口 —— 用于展示 @ControllerAdvice 的全局异常拦截
 *
 * <p>这个 Controller 里的方法会故意抛异常，
 * 由 {@code GlobalExceptionHandler}（标注了 @ControllerAdvice）统一拦截和处理。
 *
 * <h3>@ControllerAdvice 的拦截链路</h3>
 * <pre>
 *   Controller 方法抛出异常
 *       │
 *       ▼
 *   DispatcherServlet.processHandlerException()
 *       │
 *       ├─ 遍历所有 HandlerExceptionResolver（包括 @ControllerAdvice 注册的）
 *       │
 *       ├─ ExceptionHandlerExceptionResolver 找到匹配的 @ExceptionHandler 方法
 *       │
 *       ├─ 调用 GlobalExceptionHandler.handleXxx()
 *       │
 *       ▼
 *   返回统一格式的 ApiResult { code, message, data }
 * </pre>
 */
@RestController
@RequestMapping("/exception-demo")
public class ExceptionDemoController {

    private static final Logger log = LoggerFactory.getLogger(ExceptionDemoController.class);

    /**
     * 正常请求 —— 返回普通 User
     */
    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Long id) {
        log.info("📥 查询用户: id={}", id);
        if (id <= 0) {
            throw new IllegalArgumentException("用户 ID 必须大于 0，实际: " + id);
        }
        return new User(id, "张三", "zhangsan@example.com");
    }

    /**
     * 故意抛运行时异常 —— 验证 @ControllerAdvice 拦截
     */
    @GetMapping("/error/runtime")
    public String runtimeError() {
        log.info("📥 即将抛出 RuntimeException...");
        throw new RuntimeException("模拟一个未预期的运行时异常！");
    }

    /**
     * 故意抛空指针 —— 验证不同异常类型走不同的 handler
     */
    @GetMapping("/error/npe")
    public String npeError() {
        log.info("📥 即将抛出 NullPointerException...");
        String s = null;
        //noinspection ResultOfMethodCallIgnored
        s.length(); // 💥 NPE
        return s;
    }
}
