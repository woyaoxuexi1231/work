package com.example.springqa.Q20_AsyncRequest.era;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 配置 Callable 异步请求的线程池。
 * 默认是 SimpleAsyncTaskExecutor（来一个 new 一个，不限制）——
 * 这里改成固定 20 个线程，让你看到线程复用 + 排队效果。
 */
@Configuration
public class AsyncConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);    // 核心线程数——只有 3 个线程在干活
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100); // 队列——多余的请求排队
        executor.setThreadNamePrefix("async-worker-"); // 线程名前缀
        executor.initialize();

        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(30000); // Callable 超时 30s
    }
}
