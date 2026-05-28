package com.example.springqa.Q19_ExceptionHandling;

import org.springframework.stereotype.Component;

/**
 * <h1>Q19：统一异常处理 — @ControllerAdvice + @ExceptionHandler</h1>
 */
@Component
public class ExceptionHandlingDemo {

    public String runDemo() {
        return "=== Q19: 统一异常处理 ===\n\n" +
            "异常处理流程:\n" +
            "  Controller 抛异常\n" +
            "    → DispatcherServlet.processHandlerException()\n" +
            "    → 遍历 HandlerExceptionResolvers:\n" +
            "      1. ExceptionHandlerExceptionResolver（@ExceptionHandler 方法）\n" +
            "      2. ResponseStatusExceptionResolver（@ResponseStatus 注解）\n" +
            "      3. DefaultHandlerExceptionResolver（内置异常 → HTTP 状态码）\n" +
            "    → 找到匹配的 @ExceptionHandler → 反射调用\n" +
            "    → 返回值通过 HttpMessageConverter 序列化为 JSON\n\n" +
            "@RestControllerAdvice 示例:\n" +
            "  @ExceptionHandler(MethodArgumentNotValidException.class)\n" +
            "  @ResponseStatus(HttpStatus.BAD_REQUEST)\n" +
            "  public ApiError handleValidation(ex) { ... }\n\n" +
            "  @ExceptionHandler(Exception.class)\n" +
            "  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)\n" +
            "  public ApiError handleUnknown(ex) { ... }\n\n" +
            "ResponseBodyAdvice:\n" +
            "  在 @ResponseBody 序列化前拦截，统一包装返回值:\n" +
            "  Controller 返回 User → ResponseBodyAdvice → ApiResponse{code:200, data:User}\n\n" +
            "【Spring 为什么这样设计？】\n" +
            "@ControllerAdvice 是 AOP 在 Web 层的应用——异常处理从 Controller 中分离，\n" +
            "集中管理横切关注点。注解 + 方法匹配比接口实现更灵活。\n";
    }
}
