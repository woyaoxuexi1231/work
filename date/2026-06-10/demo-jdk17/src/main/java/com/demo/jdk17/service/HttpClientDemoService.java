package com.demo.jdk17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 11 HTTP Client（正式版）
 *
 * 核心思想：Java 内置的现代 HTTP 客户端，支持同步/异步、HTTP/2
 * 替代方案：HttpURLConnection（古老）或 Apache HttpClient（第三方依赖）
 */
@Service
public class HttpClientDemoService {

    private static final Logger log = LoggerFactory.getLogger(HttpClientDemoService.class);

    public Map<String, Object> demo() {
        var result = new LinkedHashMap<String, Object>();

        // 1. 创建 HttpClient（可复用，线程安全）
        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 2. 构建请求
        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/get"))
                .header("User-Agent", "JDK-HttpClient-Demo")
                .GET()
                .build();

        // 3. 同步发送
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            result.put("1_状态码", response.statusCode());
            result.put("1_响应头_ContentType", response.headers().firstValue("Content-Type").orElse("N/A"));
            // 截取响应体前200字符，避免太长
            String body = response.body();
            result.put("2_响应体_前200字符", body.substring(0, Math.min(body.length(), 200)));
        } catch (Exception e) {
            result.put("错误", "请求失败: " + e.getMessage());
            log.error("🚫 HTTP 请求失败", e);
        }

        // 4. 异步发送（返回 CompletableFuture）
        try {
            var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode);
            result.put("3_异步状态码", future.get()); // 阻塞等待结果（演示用）
        } catch (Exception e) {
            result.put("3_异步错误", e.getMessage());
            log.error("🚫 异步 HTTP 请求失败", e);
        }

        result.put("4_说明", "JDK 11 内置 HTTP Client，支持 HTTP/2、同步/异步、WebSocket，无需第三方依赖");

        log.info("✅ HTTP Client 演示完成");
        return result;
    }
}
