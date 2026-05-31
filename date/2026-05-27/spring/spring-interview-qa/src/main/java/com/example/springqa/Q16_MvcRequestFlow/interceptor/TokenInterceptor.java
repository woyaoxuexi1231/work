package com.example.springqa.Q16_MvcRequestFlow.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 检查是否为 HandlerMethod（Spring MVC 控制器方法）
        if (handler instanceof HandlerMethod) {

            HandlerMethod handlerMethod = (HandlerMethod) handler;

            // 2. 检查方法上是否有 @NoAuth 注解
            NoAuth methodAnnotation = handlerMethod.getMethodAnnotation(NoAuth.class);
            if (methodAnnotation != null) {
                return true; // 跳过校验
            }

            // 3. 检查控制器类上是否有 @NoAuth 注解（可选）
            NoAuth classAnnotation = handlerMethod.getBeanType().getAnnotation(NoAuth.class);
            if (classAnnotation != null) {
                return true; // 跳过校验
            }
        }

        // 4. 执行 Token 校验逻辑（示例：从 Header 获取 token）
        String token = request.getHeader("Authorization");
        if (token == null || !isValidToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or missing token");
            return false;
        }

        return true;
    }

    private boolean isValidToken(String token) {
        // 模拟校验逻辑
        return "secret-token".equals(token);
    }
}