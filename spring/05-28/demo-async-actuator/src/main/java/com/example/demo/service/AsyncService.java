package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 异步服务 —— 三种典型 @Async 使用模式
 */
@Service
public class AsyncService {

    private static final Logger log = LoggerFactory.getLogger(AsyncService.class);

    // ==================== 模式一：Future<T> ====================

    /**
     * 返回 Future<String> —— 调用方通过 future.get() 阻塞等待。
     * 适用场景：需要立即拿到 Future 句柄，稍后再取结果。
     */
    @Async("taskExecutor")
    public Future<String> processWithFuture(String taskName) {
        log.info("[Future] 开始处理任务: {}", taskName);
        try {
            // 模拟耗时操作（比如调第三方接口、写数据库）
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String result = taskName + " → 处理完成 (Future)";
        log.info("[Future] 任务完成: {}", taskName);
        return new AsyncResult<>(result);
    }

    // ==================== 模式二：CompletableFuture<T> ====================

    /**
     * 返回 CompletableFuture<String> —— 更强的组合能力。
     * 调用方可以不阻塞：.thenApply / .thenAccept / .exceptionally 链式处理。
     *
     * 推荐：Spring 4+ / Java 8 首选 CompletableFuture。
     */
    @Async("taskExecutor")
    public CompletableFuture<String> processWithCompletableFuture(String taskName) {
        log.info("[CompletableFuture] 开始处理任务: {}", taskName);
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 将 checked exception 转成 unchecked，让 exceptionally 能捕获
            throw new RuntimeException("任务被中断", e);
        }
        String result = taskName + " → 处理完成 (CompletableFuture)";
        log.info("[CompletableFuture] 任务完成: {}", taskName);
        return CompletableFuture.completedFuture(result);
    }

    // ==================== 模式三：抛异常 —— 演示异常处理 ====================

    /**
     * 返回 Future，但内部抛异常 —— 调用方 future.get() 会抛出 ExecutionException，
     * 原因被包装在其中，通过 ex.getCause() 获取。
     */
    @Async("taskExecutor")
    public Future<String> processWithException(String taskName) {
        log.info("[异常Future] 开始处理任务: {}", taskName);
        // 模拟业务异常
        throw new RuntimeException("任务 [" + taskName + "] 处理失败: 数据库连接超时");
    }

    /**
     * void 返回 + 抛异常 —— 触发 AsyncUncaughtExceptionHandler（全局）
     */
    @Async("taskExecutor")
    public void processVoidWithException(String taskName) {
        log.info("[void异常] 开始处理任务: {}", taskName);
        throw new IllegalArgumentException("任务 [" + taskName + "] 参数非法");
    }
}
