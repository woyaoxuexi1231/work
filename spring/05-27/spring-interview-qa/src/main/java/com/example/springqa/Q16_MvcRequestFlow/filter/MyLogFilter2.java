package com.example.springqa.Q16_MvcRequestFlow.filter;

import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
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
        HttpServletRequest req = (HttpServletRequest) request;

        // ★ 规则1：multipart 文件上传 → 直接放行，绝不碰 body
        String contentType = req.getContentType();
        if (contentType != null && contentType.startsWith("multipart/")) {
            System.out.println("[MylogFilter] multipart 请求 → 直接放行：" + req.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        // ★ 规则2：application/json → 读 body 打日志（用 RepeatableRequestWrapper 保证可重复读）
        if ("POST".equalsIgnoreCase(req.getMethod())
                && "application/json".equals(contentType)) {
            System.out.println("[MylogFilter] JSON 请求 → 记录 body：" + req.getRequestURI());

            RepeatableRequestWrapper wrappedRequest = new RepeatableRequestWrapper(req);
            byte[] requestBody = wrappedRequest.getRequestBody();
            System.out.println("  请求体：" + new String(requestBody, StandardCharsets.UTF_8));

            chain.doFilter(wrappedRequest, response);
            return;
        }

        // ★ 规则3：其他请求 → 直接放行
        chain.doFilter(request, response);
    }

}
