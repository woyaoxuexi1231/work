package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步配置：
 * 1. @EnableAsync 开启异步支持
 * 2. 实现 AsyncConfigurer 自定义线程池 + 全局异常处理
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * 自定义异步线程池
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);                    // 核心线程数
        executor.setMaxPoolSize(8);                     // 最大线程数
        executor.setQueueCapacity(100);                 // 缓冲队列大小
        executor.setKeepAliveSeconds(60);               // 空闲线程存活时间
        executor.setThreadNamePrefix("async-demo-");    // 线程名前缀
        // 拒绝策略：由调用线程执行（保证不丢任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);  // 优雅关闭
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 全局异步未捕获异常处理器
     * —— 当 @Async 方法返回 void 且内部抛异常时触发
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("异步方法 [{}] 发生未捕获异常, 参数: {}",
                        method.getName(), params, ex);
            }
        };
    }
}
