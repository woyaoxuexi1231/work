package com.example.dynamicds.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置 — 统一管理同步/初始化/业务处理线程池。
 *
 * 线程池分配策略：
 * - syncTaskExecutor（1 线程）：顺序执行同步任务，同一时间只有一个同步任务在运行
 * - initDataTaskExecutor（1 线程）：顺序执行初始化任务，防止并发灌数冲突
 * - syncBusinessExecutor（4 线程）：并发执行 4 类业务（STOCK/TRADE/POSITION/ASSET）
 * - stock/trade/position/asset PairExecutor（各 2 线程）：每类业务内部分为拉取线程 + 落库线程
 *
 * 所有线程池使用 AbortPolicy：队列满时直接抛出异常，避免静默丢任务。
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
