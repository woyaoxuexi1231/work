package com.riskdatahub.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.sync.task.SyncTaskService;
import com.riskdatahub.sync.task.entity.SyncTask;
import com.riskdatahub.sync.task.mapper.SyncTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 同步任务调度器 — 定时扫描 QUEUED 任务并提交到线程池执行。
 * <p>
 * 每 3 秒扫描一次，检测异常终止的任务（锁释放但 DB 仍 RUNNING），
 * 取出最旧的 QUEUED 任务交 {@link SyncEngine#executeTask} 执行。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
public class SyncTaskScheduler {

    private static final String LOCK_KEY = "risk-hub:sync:task:lock";

    private final RedissonClient redissonClient;
    private final SyncTaskMapper syncTaskMapper;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncTaskService syncTaskService;
    private final SyncEngine syncEngine;
    private final ThreadPoolExecutor syncTaskExecutor;

    public SyncTaskScheduler(RedissonClient redissonClient,
                             SyncTaskMapper syncTaskMapper,
                             RoutingMybatisExecutor routingMybatisExecutor,
                             SyncTaskService syncTaskService,
                             SyncEngine syncEngine,
                             @Qualifier("syncTaskExecutor") ThreadPoolExecutor syncTaskExecutor) {
        this.redissonClient = redissonClient;
        this.syncTaskMapper = syncTaskMapper;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.syncTaskService = syncTaskService;
        this.syncEngine = syncEngine;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    // ==================== 定时扫描 ====================

    /** 每 3 秒扫描一次 QUEUED 任务。 */
    @Scheduled(fixedDelay = 3000)
    public void scanAndExecute() {
        try {
            doScanAndExecute();
        } catch (Exception e) {
            log.error("[SyncScheduler] scanAndExecute 异常，下次继续", e);
        }
    }

    private void doScanAndExecute() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean lockHeld = lock.isLocked();

        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            // 检查异常终止的任务（锁释放但 DB 仍 RUNNING）
            List<SyncTask> runningTasks = syncTaskMapper.selectList(
                    new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getStatus, "RUNNING"));

            if (!runningTasks.isEmpty()) {
                if (lockHeld) {
                    return; // 正常执行中
                }
                for (SyncTask task : runningTasks) {
                    syncTaskService.updateTaskFields(task.getId(), t -> {
                        t.setStatus("FAILED");
                        t.setMessage("同步任务异常终止（Redisson 锁已释放）");
                        t.setErrorMessage("Process crashed or watchdog expired");
                        t.setFinishedAt(TimeUtils.now());
                    });
                    log.warn("[SyncScheduler] 检测到异常终止任务 id={}（锁已释放），已标记 FAILED", task.getId());
                }
            }

            // 取出最旧的 QUEUED 任务
            SyncTask queued = syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                    .eq(SyncTask::getStatus, "QUEUED")
                    .orderByAsc(SyncTask::getId)
                    .last("limit 1"));
            if (queued == null) {
                return;
            }

            Long taskId = queued.getId();
            String dsKey = queued.getDataSourceKey();
            int ps = queued.getPageSize();

            // 标记为 RUNNING
            syncTaskService.updateTaskFields(taskId, task -> {
                task.setStatus("RUNNING");
                task.setStartedAt(TimeUtils.now());
            });

            // 提交到线程池执行
            syncTaskExecutor.submit(() -> syncEngine.executeTask(taskId, dsKey, ps));
            log.info("[SyncScheduler] 调度任务 id={}, dataSourceKey={}", taskId, dsKey);
        });
    }
}
