package com.gateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;

@Component
@Order(100)
public class ProxyFilter implements Filter {

    private static final String MLM_PREFIX = "/mlm-api";
    private static final String RISK_PREFIX = "/risk-api";

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String path = request.getRequestURI();

        if (path.equals("/login") || path.equals("/logout") || path.equals("/api/auth/me")) {
            chain.doFilter(req, res);
            return;
        }

        String strippedPath;
        String targetBase;

        if (path.startsWith(MLM_PREFIX)) {
            strippedPath = path.substring(MLM_PREFIX.length());
            targetBase = "http://localhost:8088";
        } else if (path.startsWith(RISK_PREFIX)) {
            strippedPath = path.substring(RISK_PREFIX.length());
            targetBase = "http://localhost:8501";
        } else {
            chain.doFilter(req, res);
            return;
        }

        // 拼目标 URL（含 query string）
        String queryString = request.getQueryString();
        String targetUrl = targetBase + strippedPath;
        if (queryString != null) targetUrl += "?" + queryString;

        try {
            // 读取请求 body
            byte[] body = readBody(request);

            // 构建转发请求头
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if ("Host".equalsIgnoreCase(name)) continue;
                List<String> values = java.util.Collections.list(request.getHeaders(name));
                headers.addAll(name, values);
            }

            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            HttpEntity<byte[]> entity = new HttpEntity<>(body.length > 0 ? body : null, headers);

            ResponseEntity<byte[]> result = restTemplate.exchange(targetUrl, method, entity, byte[].class);

            // 写回响应
            response.setStatus(result.getStatusCode().value());
            result.getHeaders().forEach((name, values) -> {
                if (!"Transfer-Encoding".equalsIgnoreCase(name)
                    && !"Content-Length".equalsIgnoreCase(name)) {
                    values.forEach(v -> response.addHeader(name, v));
                }
            });

            byte[] respBody = result.getBody();
            if (respBody != null) {
                response.getOutputStream().write(respBody);
            }
        } catch (Exception e) {
            response.setStatus(502);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":502,\"message\":\"网关转发失败: " + e.getMessage() + "\",\"data\":null}");
        }
    }

    private byte[] readBody(HttpServletRequest request) throws IOException {
        InputStream in = request.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
