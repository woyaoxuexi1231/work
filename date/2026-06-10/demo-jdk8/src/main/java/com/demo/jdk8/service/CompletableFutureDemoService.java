package com.demo.jdk8.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 演示 JDK 8 核心特性：CompletableFuture（异步编程）
 *
 * 核心思想：Future 的增强版 —— 可链式组合、可处理异常、无需阻塞等待
 * 替代方案：Future.get() 会阻塞，且无法组合多个 Future
 */
@Slf4j
@Service
public class CompletableFutureDemoService {

    // 模拟一个耗时操作
    private String slowApiCall(String name, int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return name + "_结果";
    }

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();
        long startTime = System.currentTimeMillis();

        // 1. 基础异步执行：supplyAsync（有返回值）/ runAsync（无返回值）
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> slowApiCall("API_1", 200));
        CompletableFuture<Void> f2 = CompletableFuture.runAsync(() -> {
            log.info("✅ runAsync 任务已执行（无返回值）");
        });

        // 2. thenApply：链式转换（同步映射）
        CompletableFuture<String> f3 = CompletableFuture
                .supplyAsync(() -> slowApiCall("API_2", 200))
                .thenApply(r -> r.toUpperCase());

        // 3. 并行执行多个任务 + thenCombine 合并结果
        CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> slowApiCall("订单服务", 150));
        CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> slowApiCall("用户服务", 150));

        CompletableFuture<String> combined = orderFuture.thenCombine(userFuture,
                (order, user) -> "合并: " + order + " + " + user);

        // 4. exceptionally：异常处理（类似 catch）
        CompletableFuture<String> f4 = CompletableFuture
                .supplyAsync(() -> {
                    if (true) throw new RuntimeException("模拟服务异常");
                    return "不会到达";
                })
                .exceptionally(e -> "降级结果: " + e.getMessage());

        // 5. allOf：等待所有任务完成（并行编排）
        CompletableFuture<String> task1 = CompletableFuture.supplyAsync(() -> slowApiCall("Task1", 100));
        CompletableFuture<String> task2 = CompletableFuture.supplyAsync(() -> slowApiCall("Task2", 100));
        CompletableFuture<String> task3 = CompletableFuture.supplyAsync(() -> slowApiCall("Task3", 100));

        CompletableFuture.allOf(task1, task2, task3).join();  // 等待全部完成

        // 获取所有结果
        try {
            result.put("1_supplyAsync", f1.get());
            f2.get();
            result.put("2_thenApply_转大写", f3.get());
            result.put("3_thenCombine_合并", combined.get());
            result.put("4_exceptionally_降级", f4.get());
            result.put("5_allOf_并行", task1.get() + ", " + task2.get() + ", " + task3.get());
        } catch (Exception e) {
            log.error("🚫 CompletableFuture 异常: {}", e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        result.put("6_总耗时_ms", elapsed + "ms（3个150ms任务并行，总耗时约150ms，不是450ms）");

        log.info("✅ CompletableFuture 演示完成，总耗时: {}ms", elapsed);
        return result;
    }
}
