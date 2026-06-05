package com.riskdatahub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置 — 统一管理同步 / 业务处理线程池。
 * <p>
 * 线程池分配策略：
 * <ul>
 *   <li><b>syncTaskExecutor（1 线程）</b> — 顺序执行同步任务，同一时间只有一个同步任务在运行</li>
 *   <li><b>syncBusinessExecutor（4 线程）</b> — 并发执行 4 类业务（STOCK / TRADE / POSITION / ASSET）</li>
 *   <li><b>业务 PairExecutor（各 2 线程）</b> — 每类业务内部分为"拉取线程 + 落库线程"</li>
 * </ul>
 * </p>
 * <p>
 * 所有线程池使用 AbortPolicy：队列满时直接抛出异常，避免静默丢任务。
 * </p>
 *
 * @author risk-data-hub
 */
@Configuration
public class SyncThreadPoolConfig {

    /**
     * 同步任务线程池 — 单线程，保证同步任务顺序执行。
     *
     * @return 同步任务线程池
     */
    @Bean(name = "syncTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor syncTaskExecutor() {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(8),
                namedThreadFactory("risk-hub-sync-task-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 同步业务并发线程池 — 4 线程，并发执行 4 类业务的同步。
     *
     * @return 业务并发线程池
     */
    @Bean(name = "syncBusinessExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor syncBusinessExecutor() {
        return new ThreadPoolExecutor(
                4, 4, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(16),
                namedThreadFactory("risk-hub-business-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 股票同步双线程池（拉取 + 落库）。
     *
     * @return 股票双线程池
     */
    @Bean(name = "stockPairExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor stockPairExecutor() {
        return buildBusinessPairExecutor("risk-hub-stock-");
    }

    /**
     * 交易同步双线程池（拉取 + 落库）。
     *
     * @return 交易双线程池
     */
    @Bean(name = "tradePairExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor tradePairExecutor() {
        return buildBusinessPairExecutor("risk-hub-trade-");
    }

    /**
     * 持仓同步双线程池（拉取 + 落库）。
     *
     * @return 持仓双线程池
     */
    @Bean(name = "positionPairExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor positionPairExecutor() {
        return buildBusinessPairExecutor("risk-hub-position-");
    }

    /**
     * 资金同步双线程池（拉取 + 落库）。
     *
     * @return 资金双线程池
     */
    @Bean(name = "assetPairExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor assetPairExecutor() {
        return buildBusinessPairExecutor("risk-hub-asset-");
    }

    /**
     * 构建业务专用的双线程池（2 固定线程 + AbortPolicy）。
     *
     * @param prefix 线程名前缀
     * @return 双线程池
     */
    private ThreadPoolExecutor buildBusinessPairExecutor(String prefix) {
        return new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(8),
                namedThreadFactory(prefix),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 创建带前缀的命名线程工厂。
     *
     * @param prefix 线程名前缀
     * @return 命名线程工厂
     */
    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
