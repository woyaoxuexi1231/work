package com.mlm.web.config;

import com.mlm.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器 — 对 /api/** 路径校验 Session 登录态
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 放行登录相关 API
        if (path.startsWith("/api/auth/") || path.startsWith("/login") || path.startsWith("/logout")) {
            return true;
        }
        // 放行通知 API（允许未登录查看铃铛）
        if (path.startsWith("/api/notifications")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loginUser") == null) {
            response.sendRedirect("/login");
            return false;
        }
        return true;
    }
}
