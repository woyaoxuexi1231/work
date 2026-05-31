package com.example.demo.advice;

import com.example.demo.model.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器 —— @ControllerAdvice / @RestControllerAdvice 实战
 *
 * <h3>@ControllerAdvice 做了什么？</h3>
 * <p>它本质上是 AOP 的一种应用——为所有 Controller 织入异常处理逻辑。
 * 内部通过 {@code ExceptionHandlerExceptionResolver} 实现。
 * 当任意 Controller 抛出异常后，DispatcherServlet 调用
 * {@code processHandlerException()}，遍历已注册的
 * {@code @ExceptionHandler} 方法，找到匹配异常类型的执行。</p>
 *
 * <h3>@RestControllerAdvice vs @ControllerAdvice</h3>
 * <p>{@code @RestControllerAdvice = @ControllerAdvice + @ResponseBody}
 * —— 所有异常处理方法的返回值自动序列化为 JSON。</p>
 *
 * <h3>Spring 内置的异常处理顺序</h3>
 * <ol>
 *   <li>{@code @ExceptionHandler} 在 Controller 内部的方法（局部）</li>
 *   <li>{@code @ControllerAdvice} 中的 {@code @ExceptionHandler}（全局）👈 我们在这里</li>
 *   <li>{@code ErrorController}（Spring Boot 白标错误页）</li>
 * </ol>
 */
@RestControllerAdvice  // = @ControllerAdvice + @ResponseBody
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== 具体异常处理 ====================

    /**
     * 处理参数非法异常 —— IllegalArgumentException
     *
     * <p>当 ExceptionDemoController.getUser() 传入 id ≤ 0 时触发。
     *
     * <p>调用链路：
     * <pre>
     *   Controller 抛出 IllegalArgumentException
     *       → DispatcherServlet.processHandlerException()
     *       → ExceptionHandlerExceptionResolver.getExceptionHandlerMethod()
     *       → 匹配到本方法（参数类型匹配）
     *       → 反射调用 handleIllegalArgument()
     *       → 返回 ApiResult → 由 Jackson Converter 序列化为 JSON
     * </pre>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)  // 400
    public ApiResult<?> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("❌ 参数校验失败 [{}] {}: {}",
                request.getMethod(), request.getRequestURI(), e.getMessage());

        return ApiResult.error(400, "参数校验失败: " + e.getMessage());
    }

    // ==================== 宽泛异常兜底 ====================

    /**
     * 处理运行时异常 —— RuntimeException（兜底）
     *
     * <p>优先级：Spring 会选择异常类型最匹配的 handler。
     * NullPointerException extends RuntimeException，但没有单独的 NPE handler，
     * 所以会被这个 catch 住。如果有更精确的 handler（比如上面的 IllegalArgumentException），
     * Spring 会优先选那个。</p>
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)  // 500
    public ApiResult<?> handleRuntime(RuntimeException e, HttpServletRequest request) {
        log.error("❌ 运行时异常 [{}] {}: {}", request.getMethod(), request.getRequestURI(),
                e.getClass().getSimpleName(), e);

        return ApiResult.error(500, "服务器内部错误: " + e.getClass().getSimpleName());
    }

    // ==================== 兜底中的兜底 ====================

    /**
     * 处理所有未被上述 handler 覆盖的异常。
     *
     * <p>这是最后一道防线 —— 确保任何异常都不会把堆栈直接暴露给客户端。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleAll(Exception e, HttpServletRequest request) {
        log.error("❌ 未分类异常 [{}] {}", request.getRequestURI(), e.getClass().getSimpleName(), e);

        return ApiResult.error(500, "服务器内部错误，请联系管理员");
    }
}
