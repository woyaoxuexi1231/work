package com.example.springqa.Q18_InterceptorFilter.test;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Filter — 只拦截 /q18-test 路径的请求。
 * 注册时通过 FilterRegistrationBean 限制 URL 模式。
 */
public class TestFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;

        System.out.println("\n═══════════════════════════════════");
        System.out.println("  [TestFilter] doFilter 前置 — " + req.getMethod() + " " + req.getRequestURI());
        System.out.println("═══════════════════════════════════");

        long start = System.nanoTime();
        chain.doFilter(request, response);
        long ms = (System.nanoTime() - start) / 1_000_000;

        System.out.println("  [TestFilter] doFilter 后置 — 耗时 " + ms + "ms");
    }
}
