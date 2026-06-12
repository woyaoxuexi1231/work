package com.demo.sb3.controller;

import jakarta.servlet.http.HttpServletRequest;  // SB3 使用 jakarta.servlet（对比 SB2 的 javax.servlet）
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot 3 核心特性演示
 *
 * 对比 SB2：SB3 使用 jakarta.* 命名空间，支持虚线程，引入 RestClient/ProblemDetail 等新 API
 */
@RestController
@RequestMapping("/api")
public class Sb3FeatureController {

    private static final Logger log = LoggerFactory.getLogger(Sb3FeatureController.class);
    private final RestClient restClient;

    public Sb3FeatureController(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 1. jakarta.servlet（SB3 使用 jakarta.*，对比 SB2 的 javax.*）
     */
    @GetMapping("/hello")
    public Map<String, Object> hello(HttpServletRequest request) {
        var result = new LinkedHashMap<String, Object>();
        result.put("message", "Hello from Spring Boot 3!");
        result.put("servlet_class", request.getClass().getName());
        result.put("namespace", "jakarta.servlet（SB3 使用 jakarta.*）");
        result.put("SB2对比", "SB2 使用 javax.servlet.*，SB3 迁移到 jakarta.servlet.*");
        result.put("虚线程", "当前线程 isVirtual=" + Thread.currentThread().isVirtual());
        return result;
    }

    /**
     * 2. RestClient：Spring 6.1+ 流式 HTTP 客户端（替代 RestTemplate）
     * 对比 WebClient：同步模型 + 虚线程 = 简单又高性能
     */
    @GetMapping("/rest-client")
    public Map<String, Object> restClient() {
        var result = new LinkedHashMap<String, Object>();

        try {
            // RestClient 流式调用
            String response = restClient.get()
                    .uri("/get")
                    .retrieve()
                    .body(String.class);

            result.put("1_RestClient调用", "成功");
            result.put("2_响应体_前150字符", response != null
                    ? response.substring(0, Math.min(response.length(), 150))
                    : "null");
        } catch (Exception e) {
            result.put("错误", e.getMessage());
            log.error("🚫 RestClient 调用失败", e);
        }

        result.put("3_说明", "RestClient 是同步的，配合 spring.threads.virtual.enabled=true 性能极佳");
        result.put("4_对比", "WebClient: 响应式（复杂）；RestTemplate: 旧 API（不推荐）；RestClient: 新 API（推荐）");
        return result;
    }

    /**
     * 3. ProblemDetail：RFC 7807 标准错误格式（SB3 自动启用）
     * 调用 /api/error 触发异常，返回标准 ProblemDetail JSON
     */
    @GetMapping("/error")
    public Map<String, Object> triggerError() {
        // 抛出异常，SB3 自动返回 ProblemDetail 格式
        throw new ResourceNotFoundException("资源不存在");
    }

    /**
     * 自定义异常 + ProblemDetail
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("资源未找到");
        problem.setType(URI.create("https://example.com/errors/not-found"));
        problem.setProperty("timestamp", System.currentTimeMillis());
        problem.setProperty("hint", "请检查请求的资源 ID 是否正确");
        return problem;
    }

    static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * 4. Virtual Threads：SB3 一行配置开启虚线程
     */
    @GetMapping("/virtual")
    public Map<String, Object> virtualThread() {
        var result = new LinkedHashMap<String, Object>();
        result.put("1_当前线程", Thread.currentThread().toString());
        result.put("2_isVirtual", Thread.currentThread().isVirtual());
        result.put("3_线程名", Thread.currentThread().getName());
        result.put("4_说明", "spring.threads.virtual.enabled=true → Tomcat 请求处理线程自动变成虚线程");
        result.put("5_对比SB2", "SB2: 平台线程（200个线程池）；SB3+JDK21: 虚线程（可处理数万并发请求）");
        return result;
    }

    /**
     * 5. 查看所有 SB3 特性演示端点
     */
    @GetMapping("/features")
    public Map<String, String> listFeatures() {
        var features = new LinkedHashMap<String, String>();
        features.put("GET /api/hello", "jakarta.servlet（对比 SB2 的 javax.servlet）+ 虚线程检测");
        features.put("GET /api/rest-client", "RestClient 流式 HTTP 客户端（替代 RestTemplate）");
        features.put("GET /api/error", "ProblemDetail RFC 7807 标准错误格式");
        features.put("GET /api/virtual", "Virtual Threads 虚线程（一行配置开启）");
        features.put("GET /actuator/health", "Actuator + Micrometer Observability");
        features.put("GET /actuator/info", "Actuator 应用信息");
        features.put("GET /actuator/metrics", "Actuator 指标监控");
        return features;
    }
}
