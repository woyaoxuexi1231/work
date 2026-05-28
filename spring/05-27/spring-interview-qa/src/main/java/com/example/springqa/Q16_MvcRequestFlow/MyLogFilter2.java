package com.example.springqa.Q16_MvcRequestFlow;

import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author hulei
 * @since 2026/5/28 22:57
 */

@Component
public class MyLogFilter2 implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // 只拦截 get 请求
        HttpServletRequest req = (HttpServletRequest) request;
        if ("POST".equalsIgnoreCase(req.getMethod())) {
            System.out.println("[MylogFilter] 拦截到请求：" + req.getRequestURI());
        } else {
            chain.doFilter(request, response);
        }

        // 只拦截 /q16 路径
        if (req.getRequestURI().startsWith("/q16-post")) {
            System.out.println("[MylogFilter] 拦截到请求：" + req.getRequestURI());
        } else {
            chain.doFilter(request, response);
        }

        // 只拦截 Content-Type 为 application/json 的请求
        if ("application/json".equals(req.getContentType())) {
            System.out.println("[MylogFilter] 拦截到请求：" + req.getRequestURI());
        } else {
            chain.doFilter(request, response);
        }

        // 拿取结果
        // 如果这样写会有一个问题
        // 2026-05-28 22:45:23.718 ERROR 31976 --- [nio-8080-exec-1] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed; nested exception is java.lang.IllegalStateException: getReader() has already been called for this request] with root cause
        // 基于 HTTP 协议底层的数据传输特性以及 流式处理设计哲学。
        // 因此，Servlet 规范选择了最通用的底层抽象：一次读取、流式访问。如果需要重复读取，由开发者主动使用 ContentCachingRequestWrapper 或 getParameter() 等高层 API 来缓存
        // String bodyString = getBodyString(req);
        // System.out.println("[MylogFilter] 请求结果：" + bodyString);

        // 1. 包装原始请求和响应，开始缓存数据
        RepeatableRequestWrapper wrappedRequest = new RepeatableRequestWrapper(req);

        // 2. 提前打印结果
        byte[] requestBody = wrappedRequest.getRequestBody();
        System.out.println("请求体内容：" + new String(requestBody, StandardCharsets.UTF_8));

        // 3. 放行请求（此时 Controller 可以正常读取 body）
        chain.doFilter(wrappedRequest, response);

    }

}
