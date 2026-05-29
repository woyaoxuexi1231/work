package com.example.springqa.Q18_InterceptorFilter.test;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Interceptor — 只拦截 /q18-test 路径的请求。
 * 在 WebMvcConfig 里通过 addPathPatterns 限制。
 */
@Component
public class TestInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        System.out.println("  [TestInterceptor] preHandle — " + request.getMethod() + " " + request.getRequestURI());
        System.out.println("  [TestInterceptor] handler 类型: " + handler.getClass().getSimpleName());

        // /q18-test/admin 需要 X-Token 头
        if (request.getRequestURI().contains("/admin")) {
            String token = request.getHeader("X-Token");
            if (!"valid".equals(token)) {
                System.out.println("  [TestInterceptor] ❌ Token 无效，拦截请求");
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(401);
                response.getWriter().write("{\"code\":401,\"message\":\"X-Token header 缺失或无效\"}");
                return false;
            }
            System.out.println("  [TestInterceptor] ✅ Token 有效");
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) throws Exception {
        System.out.println("  [TestInterceptor] postHandle — Controller 已执行，准备渲染响应");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        System.out.println("  [TestInterceptor] afterCompletion — 请求处理完毕（类似 finally）");
    }
}
