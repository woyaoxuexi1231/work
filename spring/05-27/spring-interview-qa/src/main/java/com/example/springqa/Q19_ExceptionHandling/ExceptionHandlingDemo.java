package com.example.springqa.Q19_ExceptionHandling;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <h1>Q19：统一异常处理 — @ControllerAdvice + @ExceptionHandler</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>@ControllerAdvice + @ExceptionHandler 的实现原理？</li>
 *   <li>如何把异常信息以 JSON 格式返回？</li>
 *   <li>ResponseBodyAdvice 能做什么？</li>
 * </ul>
 *
 * <h2>@ControllerAdvice 实现原理</h2>
 * <p>@ControllerAdvice 本质上是一个特殊的 @Component，被
 * ExceptionHandlerExceptionResolver 扫描并注册。</p>
 *
 * <h2>异常处理流程</h2>
 * <pre>
 * Controller 抛异常
 *   → DispatcherServlet.processHandlerException()
 *   → 遍历 HandlerExceptionResolvers:
 *     1. ExceptionHandlerExceptionResolver（@ExceptionHandler 方法）
 *     2. ResponseStatusExceptionResolver（@ResponseStatus 注解）
 *     3. DefaultHandlerExceptionResolver（内置异常 → HTTP 状态码）
 *   → 找到匹配的 @ExceptionHandler → 反射调用
 *   → 返回值通过 HttpMessageConverter 序列化为 JSON
 * </pre>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>@ControllerAdvice 是"AOP 在 Web 层的应用"——让异常处理逻辑
 * 从 Controller 中分离，实现横切关注点的集中管理。</p>
 *
 * @author Spring Interview QA
 */
public class ExceptionHandlingDemo {

    public static void main(String[] args) {
        System.out.println("========== Q19: 统一异常处理 Demo ==========\n");

        explainExceptionHandlerFlow();
        System.out.println();
        showErrorResponseFormat();
        System.out.println();
        demoResponseBodyAdvice();
        System.out.println();
        showFullExample();

        System.out.println("\n========== Demo 结束 ==========");
    }

    static void explainExceptionHandlerFlow() {
        System.out.println("@ControllerAdvice 工作原理:");
        System.out.println();
        System.out.println("1. Controller 抛异常");
        System.out.println("   → DispatcherServlet.processHandlerException()");
        System.out.println();
        System.out.println("2. 遍历 HandlerExceptionResolvers（按 order 排序）");
        System.out.println();
        System.out.println("3. ExceptionHandlerExceptionResolver:");
        System.out.println("   a. 从缓存查找匹配的 @ExceptionHandler 方法");
        System.out.println("   b. 没有缓存 → 扫描所有 @ControllerAdvice Bean");
        System.out.println("      → 收集 @ExceptionHandler 方法 → 建立映射");
        System.out.println("   c. 找到匹配方法 → ServletInvocableHandlerMethod.invokeAndHandle()");
        System.out.println("      → 参数解析（可注入 request、异常对象等）");
        System.out.println("      → 反射调用 @ExceptionHandler 方法");
        System.out.println("      → 返回值处理（@ResponseBody → JSON 序列化）");
        System.out.println();
        System.out.println("4. 没找到匹配 → 继续尝试其他 Resolver → 最终 500");
        System.out.println();
        System.out.println("设计意图：异常处理是横切关注点，应该集中管理。");
        System.out.println("这是 Spring AOP 思想在 Web 层的延伸。");
    }

    static void showErrorResponseFormat() {
        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("code", 400);
        errorResponse.put("message", "参数校验失败: name 不能为空");
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("path", "/api/users");

        System.out.println("统一 JSON 错误格式示例:");
        System.out.println("  " + errorResponse.toString());

        System.out.println();
        System.out.println("实现方式:");
        System.out.println("@RestControllerAdvice  // = @ControllerAdvice + @ResponseBody");
        System.out.println("class GlobalExceptionHandler {");
        System.out.println("    @ExceptionHandler(MethodArgumentNotValidException.class)");
        System.out.println("    @ResponseStatus(HttpStatus.BAD_REQUEST)");
        System.out.println("    public ApiError handleValidation(MethodArgumentNotValidException ex) {");
        System.out.println("        String msg = ex.getBindingResult().getFieldErrors().stream()");
        System.out.println("            .map(e -> e.getField() + \": \" + e.getDefaultMessage())");
        System.out.println("            .collect(Collectors.joining(\", \"));");
        System.out.println("        return new ApiError(400, msg);");
        System.out.println("    }");
        System.out.println();
        System.out.println("    @ExceptionHandler(Exception.class)");
        System.out.println("    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)");
        System.out.println("    public ApiError handleUnknown(Exception ex) {");
        System.out.println("        log.error(\"未知异常\", ex);");
        System.out.println("        return new ApiError(500, \"服务器内部错误\");");
        System.out.println("    }");
        System.out.println("}");
    }

    static void demoResponseBodyAdvice() {
        System.out.println("ResponseBodyAdvice 的作用:");
        System.out.println("在 @ResponseBody 序列化之前拦截和修改返回值。");
        System.out.println();
        System.out.println("典型场景 —— 统一包装返回结果:");
        System.out.println("@RestControllerAdvice");
        System.out.println("class ApiResponseWrapper implements ResponseBodyAdvice<Object> {");
        System.out.println("    @Override");
        System.out.println("    public boolean supports(MethodParameter returnType,");
        System.out.println("            Class<? extends HttpMessageConverter<?>> converterType) {");
        System.out.println("        // 避免双重包装");
        System.out.println("        return !returnType.getParameterType().equals(ApiResponse.class);");
        System.out.println("    }");
        System.out.println("    @Override");
        System.out.println("    public Object beforeBodyWrite(Object body, ...) {");
        System.out.println("        // 统一包装成 {code: 200, message: \"success\", data: body}");
        System.out.println("        return ApiResponse.success(body);");
        System.out.println("    }");
        System.out.println("}");
        System.out.println();
        System.out.println("这样 Controller 只需要返回业务对象，");
        System.out.println("前端收到的始终是统一的 ApiResponse 格式。");
        System.out.println();
        System.out.println("设计意图：解决前后端分离项目中\"手动包装返回结果\"的重复代码问题。");
    }

    static void showFullExample() {
        System.out.println("// ============ 完整的异常处理 + 统一响应 ============");
        System.out.println();
        System.out.println("// 1. 统一的 API 响应对象");
        System.out.println("class ApiResponse<T> {");
        System.out.println("    private int code;");
        System.out.println("    private String message;");
        System.out.println("    private T data;");
        System.out.println("    static <T> ApiResponse<T> success(T data) { ... }");
        System.out.println("    static <T> ApiResponse<T> error(int code, String msg) { ... }");
        System.out.println("}");
        System.out.println();
        System.out.println("// 2. 全局异常处理 + 3. 统一响应包装");
        System.out.println("// → Controller 代码非常干净");
        System.out.println("@RestController");
        System.out.println("class UserController {");
        System.out.println("    @GetMapping(\"/users/{id}\")");
        System.out.println("    public User getUser(@PathVariable Long id) {");
        System.out.println("        return userService.findById(id);");
        System.out.println("        // 自动包装成 ApiResponse");
        System.out.println("        // 异常自动被 GlobalExceptionHandler 捕获");
        System.out.println("    }");
        System.out.println("}");
    }
}
