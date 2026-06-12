package com.demo.jdk21.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;

/**
 * 演示 JDK 21 StructuredTaskScope（结构化并发）—— 预览特性
 *
 * 核心思想：多个子任务绑定在一个 scope 内，异常自动传播，任务自动取消
 * 替代方案：CompletableFuture（异常处理复杂，需要手动管理取消）
 */
@Service
public class StructuredTaskDemoService {

    private static final Logger log = LoggerFactory.getLogger(StructuredTaskDemoService.class);

    // 模拟不同服务的调用
    private String fetchUser(int id) throws InterruptedException {
        Thread.sleep(100);
        return "User_" + id;
    }

    private String fetchOrder(int id) throws InterruptedException {
        Thread.sleep(150);
        return "Order_" + id;
    }

    private String fetchRecommendation(int id) throws InterruptedException {
        Thread.sleep(200);
        return "Recommendation_for_" + id;
    }

    public Map<String, Object> demo() {
        var result = new LinkedHashMap<String, Object>();
        int userId = 42;

        // 1. ShutdownOnFailure：任一失败 → 取消所有其他任务 + 抛异常
        long start = System.currentTimeMillis();
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // fork 多个子任务（都在同一个 scope 内）
            var userTask = scope.fork(() -> fetchUser(userId));
            var orderTask = scope.fork(() -> fetchOrder(userId));
            var recTask = scope.fork(() -> fetchRecommendation(userId));

            scope.join();            // 等待所有任务完成
            scope.throwIfFailed();   // 任一失败则抛异常

            // 全部成功，获取结果
            result.put("1_user", userTask.get());
            result.put("1_order", orderTask.get());
            result.put("1_recommendation", recTask.get());

        } catch (Exception e) {
            result.put("1_错误", e.getMessage());
            log.error("🚫 结构化并发失败", e);
        }
        result.put("1_耗时_ms", (System.currentTimeMillis() - start) + "ms（并行执行，取最慢的）");

        // 2. ShutdownOnSuccess：任一成功 → 取消其他任务（竞速模式）
        long raceStart = System.currentTimeMillis();
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
            // 三个服务竞速，谁先返回用谁的结果
            scope.fork(() -> {
                Thread.sleep(300);
                return "慢服务结果";
            });
            scope.fork(() -> {
                Thread.sleep(100);
                return "快服务结果";
            });
            scope.fork(() -> {
                Thread.sleep(200);
                return "中速服务结果";
            });

            scope.join();
            result.put("2_竞速结果", scope.result());  // 最快的结果

        } catch (Exception e) {
            result.put("2_竞速错误", e.getMessage());
            log.error("🚫 竞速失败", e);
        }
        result.put("2_竞速耗时_ms", (System.currentTimeMillis() - raceStart) + "ms（最快100ms就返回，不等300ms）");

        result.put("3_对比", "CompletableFuture: 需要手动 allOf/anyOf + 手动取消；StructuredTaskScope: 自动管理生命周期");

        log.info("✅ StructuredTaskScope 演示完成");
        return result;
    }
}
public class StructuredTaskDemoService {
    
}
