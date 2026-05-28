package com.example.springqa.Q16_MvcRequestFlow;

import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * @author hulei
 * @since 2026/5/28 22:29
 */

// @Component
public class MylogFilter implements Filter {

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
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(req);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper((HttpServletResponse) response);


        // 2. 放行请求（此时 Controller 可以正常读取 body）
        chain.doFilter(wrappedRequest, wrappedResponse);

        // 3. 请求处理完毕后，从 wrapper 中获取缓存的请求体（字节数组）
        // ContentCachingRequestWrapper 是懒缓存，只有在实际读取请求体时才会填充缓存。因此，在 doFilter 之前无人读取，缓存为空；doFilter 过程中下游组件读取了请求体，缓存被填充，之后才能拿到内容。
        byte[] requestBody = wrappedRequest.getContentAsByteArray();
        if (requestBody.length > 0) {
            String bodyStr = new String(requestBody, StandardCharsets.UTF_8);
            System.out.println("请求体内容：" + bodyStr);
        }

        // 4. 如果需要读取或修改响应体，可以从 wrappedResponse 中获取
        byte[] responseBody = wrappedResponse.getContentAsByteArray();
        if (responseBody.length > 0) {
            String responseStr = new String(responseBody, StandardCharsets.UTF_8);
            System.out.println("响应体内容：" + responseStr);
        }

        // 5. 将缓存的响应体写回客户端（必须调用）
        wrappedResponse.copyBodyToResponse();
    }

    // 方式一：使用 BufferedReader 按行读取（推荐）
    public static String getBodyString(HttpServletRequest request) throws IOException {
        try (BufferedReader reader = request.getReader()) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    // 方式二：直接从 InputStream 读取字节（手动循环）
    public static String getBodyString2(HttpServletRequest request) throws IOException {
        InputStream is = request.getInputStream();
        byte[] buffer = new byte[1024];
        StringBuilder sb = new StringBuilder();
        int len;
        while ((len = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
