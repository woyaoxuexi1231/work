package com.example.dynamicds.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 同步任务和初始化任务都会长期复用这些线程池。
 * 这里统一交给 Spring 管理，避免每次执行任务时临时 new 一个新的线程池。
 */
@Configuration
public class SyncThreadPoolConfig {

    @Bean(name = "syncTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor syncTaskExecutor() {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(8),
                namedThreadFactory("risk-hub-sync-task-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "initDataTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor initDataTaskExecutor() {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(4),
                namedThreadFactory("risk-hub-init-task-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "syncBusinessExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor syncBusinessExecutor() {
        return new ThreadPoolExecutor(
                4, 4, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(16),
                namedThreadFactory("risk-hub-business-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "stockPairExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor stockPairExecutor() {
        return buildBusinessPairExecutor("risk-hub-stock-");
    }

    @Bean(name = "tradePairExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor tradePairExecutor() {
        return buildBusinessPairExecutor("risk-hub-trade-");
    }

    @Bean(name = "positionPairExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor positionPairExecutor() {
        return buildBusinessPairExecutor("risk-hub-position-");
    }

    @Bean(name = "assetPairExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor assetPairExecutor() {
        return buildBusinessPairExecutor("risk-hub-asset-");
    }

    private ThreadPoolExecutor buildBusinessPairExecutor(String prefix) {
        return new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(8),
                namedThreadFactory(prefix),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
