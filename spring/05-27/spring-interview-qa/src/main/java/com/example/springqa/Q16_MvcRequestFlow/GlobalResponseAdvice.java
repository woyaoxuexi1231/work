package com.example.springqa.Q16_MvcRequestFlow;

import com.alibaba.fastjson2.JSON;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice // 等价于 @ControllerAdvice + @ResponseBody
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 可以判断是否要包装：比如跳过 Swagger 相关接口、或者已经包装过的接口
        // 此处简单起见，对所有返回都包装

        // 当请求发生异常并被 @ExceptionHandler 处理时，ResponseBodyAdvice 的 supports 方法会被调用两次（或至少一次），且 MethodParameter 指向的是异常处理器方法（如 handleGeneric），而不是原始 Controller 方法。
        // ResponseBodyAdvice 的工作机制是：在 HandlerMethod 执行完毕后，无论返回的是正常响应还是由 @ExceptionHandler 产生的异常响应，最终都会经过 ResponseBodyAdvice 的处理（只要响应体是通过 @ResponseBody 或 ResponseEntity 写入的）。
        System.out.println("拦截到的方法：" + returnType.getMethod().getName());
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // 如果已经是统一格式，直接返回（避免重复包装）
        if (body instanceof ApiResponse) {
            return body;
        }
        // 如果响应体是 String，需要特殊处理：因为 StringHttpMessageConverter 会直接写入字符串，
        // 若返回 ApiResponse 对象，Spring 会尝试用其他 converter 序列化，可能引起类型错误。
        // 解决方案：返回 JSON 字符串，或者单独处理。
        if (body instanceof String) {
            // 转换为 JSON 字符串返回
            return JSON.toJSONString(ApiResponse.success(body));
        }
        // 正常包装
        return ApiResponse.success(body);
    }
}