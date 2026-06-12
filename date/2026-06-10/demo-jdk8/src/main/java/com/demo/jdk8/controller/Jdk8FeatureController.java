package com.demo.jdk8.controller;

import com.demo.jdk8.service.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDK 8 所有新特性的统一演示入口
 *
 * 端点列表：
 * GET /lambda              → Lambda 表达式
 * GET /stream              → Stream API
 * GET /optional            → Optional
 * GET /default-method      → 默认方法
 * GET /method-ref          → 方法引用
 * GET /datetime            → java.time 日期时间
 * GET /completable-future  → CompletableFuture 异步编排
 * GET /repeatable-annotation → 重复注解
 */
@RestController
@RequestMapping("/api")
public class Jdk8FeatureController {

    private final LambdaDemoService lambdaService;
    private final StreamDemoService streamService;
    private final OptionalDemoService optionalService;
    private final DefaultMethodDemoService defaultMethodService;
    private final MethodRefDemoService methodRefService;
    private final DateTimeDemoService dateTimeService;
    private final CompletableFutureDemoService completableFutureService;
    private final RepeatableAnnotationDemoService repeatableAnnotationService;

    public Jdk8FeatureController(
            LambdaDemoService lambdaService,
            StreamDemoService streamService,
            OptionalDemoService optionalService,
            DefaultMethodDemoService defaultMethodService,
            MethodRefDemoService methodRefService,
            DateTimeDemoService dateTimeService,
            CompletableFutureDemoService completableFutureService,
            RepeatableAnnotationDemoService repeatableAnnotationService) {
        this.lambdaService = lambdaService;
        this.streamService = streamService;
        this.optionalService = optionalService;
        this.defaultMethodService = defaultMethodService;
        this.methodRefService = methodRefService;
        this.dateTimeService = dateTimeService;
        this.completableFutureService = completableFutureService;
        this.repeatableAnnotationService = repeatableAnnotationService;
    }

    @GetMapping("/lambda")
    public Map<String, Object> lambda() {
        return lambdaService.demo();
    }

    @GetMapping("/stream")
    public Map<String, Object> stream() {
        return streamService.demo();
    }

    @GetMapping("/optional")
    public Map<String, Object> optional() {
        return optionalService.demo();
    }

    @GetMapping("/default-method")
    public Map<String, Object> defaultMethod() {
        return defaultMethodService.demo();
    }

    @GetMapping("/method-ref")
    public Map<String, Object> methodRef() {
        return methodRefService.demo();
    }

    @GetMapping("/datetime")
    public Map<String, Object> datetime() {
        return dateTimeService.demo();
    }

    @GetMapping("/completable-future")
    public Map<String, Object> completableFuture() {
        return completableFutureService.demo();
    }

    @GetMapping("/repeatable-annotation")
    public Map<String, Object> repeatableAnnotation() throws NoSuchMethodException {
        return repeatableAnnotationService.demo();
    }

    /**
     * 查看所有可用端点
     */
    @GetMapping("/features")
    public Map<String, String> listFeatures() {
        Map<String, String> features = new LinkedHashMap<>();
        features.put("GET /api/lambda", "Lambda 表达式：排序、过滤、转换");
        features.put("GET /api/stream", "Stream API：filter/map/reduce/collect/groupingBy/flatMap");
        features.put("GET /api/optional", "Optional：ifPresent/map/orElse/flatMap/filter");
        features.put("GET /api/default-method", "默认方法：接口 default 实现 + static 方法");
        features.put("GET /api/method-ref", "方法引用：静态/实例/任意对象/构造器 4 种形式");
        features.put("GET /api/datetime", "java.time：LocalDate/Duration/Period/ZonedDateTime");
        features.put("GET /api/completable-future", "CompletableFuture：异步编排 + 链式 + 并行");
        features.put("GET /api/repeatable-annotation", "@Repeatable：同一位置使用多次注解");
        return features;
    }
}
