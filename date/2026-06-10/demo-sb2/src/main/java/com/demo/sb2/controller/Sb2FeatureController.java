package com.demo.sb2.controller;

import com.demo.sb2.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.servlet.http.HttpServletRequest;  // SB2 使用 javax.servlet
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot 2 核心特性演示
 *
 * 对比 SB3：SB2 使用 javax.* 命名空间，SB3 使用 jakarta.*
 */
@RestController
@RequestMapping("/api")
public class Sb2FeatureController {

    private final AppProperties appProperties;

    public Sb2FeatureController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 1. 传统 Servlet 模型（javax.servlet）
     * SB3 对比：这里用 javax.servlet.http.HttpServletRequest
     *          SB3 用 jakarta.servlet.http.HttpServletRequest
     */
    @GetMapping("/hello")
    public Map<String, Object> hello(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Hello from Spring Boot 2!");
        result.put("servlet_class", request.getClass().getName());
        result.put("namespace", "javax.servlet（SB2 使用 javax.*）");
        result.put("SB3对比", "SB3 使用 jakarta.servlet.*");
        return result;
    }

    /**
     * 2. WebFlux 响应式编程（SB2 引入）
     * Mono：0 或 1 个元素的异步序列（类似 Optional）
     * Flux：0 到 N 个元素的异步序列（类似 Stream）
     */
    @GetMapping("/reactive/mono")
    public Mono<Map<String, Object>> reactiveMono() {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "Mono（单个异步结果）");
            result.put("thread", Thread.currentThread().getName());
            result.put("message", "这是响应式返回，非阻塞");
            return result;
        });
    }

    @GetMapping("/reactive/flux")
    public Flux<String> reactiveFlux() {
        // 每秒发射一个元素，共 5 个（SSE 流式响应）
        return Flux.interval(Duration.ofSeconds(1))
                .take(5)
                .map(i -> "第 " + (i + 1) + " 个元素 (时间: " + System.currentTimeMillis() + ")");
    }

    /**
     * 3. @ConfigurationProperties 配置绑定（SB2 增强）
     */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app_name", appProperties.getName());
        result.put("app_version", appProperties.getVersion());
        result.put("features", appProperties.getFeatures());
        result.put("db_host", appProperties.getDatabase().getHost());
        result.put("db_port", appProperties.getDatabase().getPort());
        result.put("db_name", appProperties.getDatabase().getName());
        result.put("SB2说明", "使用 @ConfigurationProperties + @Data（可变对象）");
        result.put("SB3对比", "SB3 推荐 record + @ConstructorBinding（不可变对象）");
        return result;
    }

    /**
     * 4. javax.validation 演示（SB2）
     * SB3 对比：SB3 使用 jakarta.validation
     */
    @GetMapping("/validation-info")
    public Map<String, String> validationInfo() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("SB2_注解", "@NotBlank, @Email, @Size — 来自 javax.validation.constraints");
        result.put("SB3_注解", "@NotBlank, @Email, @Size — 来自 jakarta.validation.constraints");
        result.put("变化", "包名从 javax 改为 jakarta，注解本身完全相同");
        return result;
    }

    /**
     * 查看所有 SB2 特性演示端点
     */
    @GetMapping("/features")
    public Map<String, String> listFeatures() {
        Map<String, String> features = new LinkedHashMap<>();
        features.put("GET /api/hello", "传统 Servlet（javax.servlet）");
        features.put("GET /api/reactive/mono", "WebFlux Mono 响应式");
        features.put("GET /api/reactive/flux", "WebFlux Flux 流式响应（SSE）");
        features.put("GET /api/config", "@ConfigurationProperties 配置绑定");
        features.put("GET /api/validation-info", "javax.validation vs jakarta.validation");
        features.put("GET /actuator/health", "Actuator 健康检查");
        features.put("GET /actuator/info", "Actuator 应用信息");
        features.put("GET /actuator/metrics", "Actuator 指标监控");
        features.put("GET /actuator/env", "Actuator 环境变量");
        return features;
    }
}
