package com.example.demo.controller;

import com.example.demo.health.RedisHealthIndicator;
import com.example.demo.service.AsyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 演示端点 —— 展示 @Async 三种模式 + 健康检查切换
 */
@RestController
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    @Autowired
    private AsyncService asyncService;

    // ==================== @Async 演示 ====================

    /**
     * 方式一：Future.get() 阻塞获取
     *
     * curl "http://localhost:8080/async/future?task=订单导出"
     */
    @GetMapping("/async/future")
    public String asyncWithFuture(@RequestParam(defaultValue = "default-task") String task) {
        log.info("Controller 收到请求, 提交异步任务: {}", task);
        long start = System.currentTimeMillis();

        // 提交任务，立即返回 Future 句柄（不阻塞）
        Future<String> future = asyncService.processWithFuture(task);

        // 主线程可以干别的事……
        log.info("主线程去做其他事情...");

        try {
            // get() 阻塞等待结果，最多等 5 秒
            String result = future.get(5, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            return String.format("✅ 异步任务完成 (耗时 %d ms): %s", elapsed, result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "❌ 等待被中断: " + e.getMessage();
        } catch (ExecutionException e) {
            // getCause() 拿到异步方法里真正的异常
            return "❌ 异步执行异常: " + e.getCause().getMessage();
        } catch (Exception e) {
            return "❌ 超时或其他异常: " + e.getMessage();
        }
    }

    /**
     * 方式二：CompletableFuture —— 非阻塞回调
     *
     * curl "http://localhost:8080/async/completable?task=发送邮件"
     */
    @GetMapping("/async/completable")
    public String asyncWithCompletableFuture(@RequestParam(defaultValue = "default-task") String task) {
        log.info("Controller 收到请求, 提交异步任务 (CompletableFuture): {}", task);

        CompletableFuture<String> future = asyncService.processWithCompletableFuture(task);

        // 非阻塞：注册回调，不阻塞 Tomcat 线程
        future
                .thenApply(result -> "✅ 成功: " + result)
                .exceptionally(ex -> "❌ 异常: " + ex.getMessage())
                .thenAccept(finalResult -> log.info("回调完成: {}", finalResult));

        // 立刻返回，不等待异步完成
        return "异步任务已提交（CompletableFuture 模式）。结果将通过日志回调输出，或访问 /async/completable/result 查看。";
    }

    /**
     * 方式三：异常演示 —— Future 包装的异常
     *
     * curl "http://localhost:8080/async/exception?task=错误任务"
     */
    @GetMapping("/async/exception")
    public String asyncWithException(@RequestParam(defaultValue = "error-task") String task) {
        try {
            Future<String> future = asyncService.processWithException(task);
            // 这里会抛出 ExecutionException
            future.get();
            return "这行不会执行到";
        } catch (ExecutionException e) {
            // ✅ 正确的异常处理方式：getCause() 拿到业务异常
            Throwable cause = e.getCause();
            return "❌ 捕获到业务异常: [" + cause.getClass().getSimpleName()
                    + "] " + cause.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "被中断";
        }
    }

    /**
     * 方式四：void 异步异常 —— 触发全局 AsyncUncaughtExceptionHandler
     *
     * curl "http://localhost:8080/async/void-exception?task=非法参数"
     * 观察控制台日志 —— 异常被 AsyncConfig 中的全局处理器捕获
     */
    @GetMapping("/async/void-exception")
    public String asyncVoidWithException(@RequestParam(defaultValue = "illegal-task") String task) {
        asyncService.processVoidWithException(task);
        return "异步任务已提交（void 模式）。异常不会抛到 Controller，请查看控制台日志。";
    }

    // ==================== 健康检查演示 ====================

    /**
     * 切换 Redis 健康状态 —— 模拟 Redis 宕机/恢复
     *
     * curl "http://localhost:8080/health/toggle?up=false"   # 模拟宕机
     * curl "http://localhost:8080/health/toggle?up=true"    # 模拟恢复
     *
     * 然后访问: http://localhost:8080/actuator/health
     */
    @GetMapping("/health/toggle")
    public String toggleRedisHealth(@RequestParam boolean up) {
        RedisHealthIndicator.simulateUp = up;
        return "Redis 健康状态已切换为: " + (up ? "UP ✅" : "DOWN ❌")
                + "。请访问 /actuator/health 查看效果。";
    }
}
