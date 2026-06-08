package com.riskdatahub.common.util;

import com.riskdatahub.sync.task.SyncTaskService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁操作模板 — 封装 Redisson 锁的获取与释放，消除重复的 try-finally 模板代码。
 * <p>
 * {@link SyncTaskService} 中的锁操作逻辑使用此类统一管理。
 * </p>
 * <p>
 * <b>用法示例：</b>
 * <pre>
 * lockTemplate.tryLock(lock, ownerId, () -> {
 *     // 受锁保护的业务逻辑
 *     return result;
 * });
 * </pre>
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Component
public class DistributedLockTemplate {

    /** 等待获取锁的超时时间 */
    private static final long TRY_LOCK_TIMEOUT_MS = 100;

    /**
     * 尝试获取分布式锁，成功则执行业务逻辑，最终释放锁。
     *
     * @param lock    Redisson 锁对象
     * @param ownerId 锁持有者标识
     * @param action  受锁保护的业务逻辑
     * @param <T>     返回值类型
     * @return 业务逻辑执行结果
     * @throws IllegalStateException 获取锁失败或执行被中断
     */
    public <T> T tryLock(RLock lock, long ownerId, Supplier<T> action) {
        boolean acquired = false;
        try {
            acquired = lock.tryLock(TRY_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取分布式锁被中断", e);
        }
        if (!acquired) {
            throw new IllegalStateException("已有任务正在运行，无法获取锁");
        }
        try {
            return action.get();
        } finally {
            releaseQuietly(lock);
        }
    }

    /**
     * 尝试获取分布式锁，成功则执行业务逻辑（无返回值），最终释放锁。
     *
     * @param lock    Redisson 锁对象
     * @param ownerId 锁持有者标识
     * @param action  受锁保护的业务逻辑
     * @throws IllegalStateException 获取锁失败或执行被中断
     */
    public void tryLock(RLock lock, long ownerId, Runnable action) {
        tryLock(lock, ownerId, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 静默释放锁，不抛出异常。
     *
     * @param lock Redisson 锁对象
     */
    public void releaseQuietly(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("[锁释放] 释放锁失败", e);
        }
    }
}
