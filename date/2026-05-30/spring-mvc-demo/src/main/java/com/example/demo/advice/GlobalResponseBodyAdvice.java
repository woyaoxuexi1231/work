package com.example.demo.advice;

import com.example.demo.model.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一响应体包装器 —— ResponseBodyAdvice 实战
 *
 * <h3>这是 @ControllerAdvice 的另一个强大用法</h3>
 * <p>不是处理异常，而是在 controller 方法正常返回后，<b>在序列化之前</b>拦截返回值，
 * 统一包装成 {@code ApiResult { code, message, data }} 格式。</p>
 *
 * <h3>执行时机</h3>
 * <pre>
 *   Controller 方法返回 User 对象
 *       │
 *       ▼
 *   RequestResponseBodyMethodProcessor.handleReturnValue()
 *       │
 *       ├─ 遍历所有 ResponseBodyAdvice
 *       │
 *       ├─ GlobalResponseBodyAdvice.beforeBodyWrite()
 *       │    └─ 把 User 包装成 ApiResult&lt;User&gt;
 *       │
 *       ▼
 *   HttpMessageConverter.write() → 序列化 ApiResult 为 JSON
 * </pre>
 *
 * <h3>对比</h3>
 * <ul>
 *   <li>{@code @ExceptionHandler} —— 处理异常 → 返回错误响应</li>
 *   <li>{@code ResponseBodyAdvice} —— 处理正常返回 → 统一包装</li>
 *   <li>{@code @InitBinder} —— 数据绑定预处理</li>
 *   <li>{@code @ModelAttribute} —— 全局模型属性</li>
 * </ul>
 *
 * <p>这 4 个注释构成了 @ControllerAdvice 的完整能力集。
 */
@RestControllerAdvice
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(GlobalResponseBodyAdvice.class);

    /**
     * 判断是否需要本 Advice 处理。
     *
     * <p>返回 true → beforeBodyWrite 会被调用；返回 false → 跳过。
     * 可以用来排除某些不需要包装的接口（比如文件下载、健康检查等）。
     */
    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {

        // 如果返回值已经是 ApiResult 了，不再包装（避免 ApiResult<ApiResult<...>>）
        if (returnType.getParameterType().equals(ApiResult.class)) {
            log.debug("返回值已是 ApiResult，跳过包装");
            return false;
        }

        // 排除 String 类型（String 由 StringHttpMessageConverter 处理，包装后会有类型问题）
        if (returnType.getParameterType().equals(String.class)) {
            log.debug("返回值是 String，跳过包装");
            return false;
        }

        log.debug("ResponseBodyAdvice 拦截: {}", returnType.getParameterType().getSimpleName());
        return true;
    }

    /**
     * 在 body 被序列化之前调用，可以替换 / 包装返回值。
     *
     * <p>这里的逻辑：把 Controller 的原始返回值包一层 ApiResult.success()。
     * 前端收到的始终是 { code: 200, message: "success", data: ... }。
     *
     * @param body Controller 方法的原始返回值（比如 User 对象）
     * @return 包装后的 ApiResult
     */
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        if (body == null) {
            log.info("📦 包装空返回值 → ApiResult.success(null)");
            return ApiResult.success(null);
        }

        log.info("📦 包装返回值: {} → ApiResult.success({})",
                body.getClass().getSimpleName(),
                body.toString().substring(0, Math.min(50, body.toString().length())));

        return ApiResult.success(body);
    }
}
