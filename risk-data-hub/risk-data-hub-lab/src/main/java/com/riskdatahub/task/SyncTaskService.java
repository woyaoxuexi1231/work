package com.riskdatahub.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.common.util.DistributedLockTemplate;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.RabbitMqSender;
import com.riskdatahub.sync.SyncOrchestrator;
import com.riskdatahub.sync.model.SyncResultDTO;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.task.entity.SyncBusinessRecord;
import com.riskdatahub.task.entity.SyncTask;
import com.riskdatahub.task.mapper.SyncBusinessRecordMapper;
import com.riskdatahub.task.mapper.SyncTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 同步任务服务 — 管理同步任务的提交、执行和状态查询。
 * <p>
 * 通过 Redisson 分布式锁保证同一时间只有一个同步任务在运行。
 * 使用 {@link DistributedLockTemplate} 统一管理锁的获取和释放。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
public class SyncTaskService {

    private static final String LOCK_KEY = "risk-hub:sync:task:lock";
    private static final String TAG_SYNC_TASK = "sync_task";
    private static final String TAG_SYNC_BUSINESS_RECORD = "sync_business_record";

    private final RedissonClient redissonClient;
    private final DistributedLockTemplate lockTemplate;
    private final LeafSegmentService leafSegmentService;
    private final SyncOrchestrator syncOrchestrator;
    private final DataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;
    private final RabbitMqSender rabbitMqSender;
    private final ThreadPoolExecutor syncTaskExecutor;

    public SyncTaskService(RedissonClient redissonClient,
                           DistributedLockTemplate lockTemplate,
                           LeafSegmentService leafSegmentService,
                           SyncOrchestrator syncOrchestrator,
                           DataSourceManager dataSourceManager,
                           RoutingMybatisExecutor routingMybatisExecutor,
                           SyncTaskMapper syncTaskMapper,
                           SyncBusinessRecordMapper syncBusinessRecordMapper,
                           RabbitMqSender rabbitMqSender,
                           @Qualifier("syncTaskExecutor") ThreadPoolExecutor syncTaskExecutor) {
        this.redissonClient = redissonClient;
        this.lockTemplate = lockTemplate;
        this.leafSegmentService = leafSegmentService;
        this.syncOrchestrator = syncOrchestrator;
        this.dataSourceManager = dataSourceManager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.syncTaskMapper = syncTaskMapper;
        this.syncBusinessRecordMapper = syncBusinessRecordMapper;
        this.rabbitMqSender = rabbitMqSender;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    /**
     * 提交异步同步任务。
     * <p>
     * 校验数据源 → 获取分布式锁 → 持久化任务记录 → 提交到线程池执行。
     * </p>
     *
     * @param dataSourceKey 数据源标识
     * @param pageSize      每页大小
     * @return 刚创建的同步任务对象
     * @throws IllegalArgumentException 数据源不存在或为中台库
     * @throws IllegalStateException    已有任务在运行
     */
    public SyncTask startTask(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (HubConstants.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }

        long taskId = leafSegmentService.nextId(TAG_SYNC_TASK);
        RLock lock = redissonClient.getLock(LOCK_KEY);

        return lockTemplate.tryLock(lock, taskId, () -> {
            String now = TimeUtils.now();
            int safePageSize = Math.max(1, Math.min(pageSize, 500));

            SyncTask task = new SyncTask();
            task.setId(taskId);
            task.setStatus("QUEUED");
            task.setProgress(0);
            task.setDataSourceKey(dataSourceKey);
            task.setDataSourceName(config.getName());
            task.setDatasourceType(config.getDatasourceType());
            task.setPageSize(safePageSize);
            task.setSubmittedAt(now);
            task.setMessage("同步任务已提交");
            task.setRunning(true);
            routingMybatisExecutor.run(HubConstants.DS_HUB, () -> syncTaskMapper.insert(task));

            log.info("[SyncTask] submit id={}, dataSourceKey={}, pageSize={}", task.getId(), dataSourceKey, safePageSize);
            syncTaskExecutor.submit(() -> runTask(task.getId(), dataSourceKey, safePageSize));
            return task;
        });
    }

    /**
     * 查询当前同步任务（最近一条）。
     *
     * @return 当前任务，无任务时返回 IDLE 状态的虚拟任务
     */
    public SyncTask currentTask() {
        SyncTask task = routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                        .orderByDesc(SyncTask::getId)
                        .last("limit 1")));
        if (task == null) {
            SyncTask idle = new SyncTask();
            idle.setStatus("IDLE");
            idle.setProgress(0);
            idle.setMessage("暂无同步任务");
            idle.setRunning(false);
            return idle;
        }
        task.setRunning("QUEUED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()));
        return task;
    }

    /**
     * 异步执行同步任务：执行同步 → 记录业务结果 → 更新状态 → 发送完成消息。
     */
    private void runTask(Long id, String dataSourceKey, int pageSize) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            updateTaskFields(id, task -> {
                task.setStatus("RUNNING");
                task.setStartedAt(TimeUtils.now());
            });

            SyncResultDTO result = syncOrchestrator.syncByDataSource(dataSourceKey, pageSize, p -> {
            });

            int totalPulled = 0;
            int totalSaved = 0;

            for (Map.Entry<String, BusinessSyncResult> entry : result.getBusinessResults().entrySet()) {
                String bizCode = entry.getKey();
                BusinessSyncResult bizResult = entry.getValue();

                SyncBusinessRecord record = new SyncBusinessRecord();
                record.setId(leafSegmentService.nextId(TAG_SYNC_BUSINESS_RECORD));
                record.setTaskId(id);
                record.setBusinessCode(bizCode);
                record.setStatus("SUCCESS");
                record.setPageCount(bizResult.getPageCount());
                record.setPulledCount(bizResult.getPulledCount());
                record.setSavedCount(bizResult.getSavedCount());
                record.setLastRowId(bizResult.getLastRowId());
                record.setStartedAt(TimeUtils.now());
                record.setFinishedAt(TimeUtils.now());
                routingMybatisExecutor.run(HubConstants.DS_HUB,
                        () -> syncBusinessRecordMapper.insert(record));

                totalPulled += bizResult.getPulledCount();
                totalSaved += bizResult.getSavedCount();
            }

            int finalPulled = totalPulled;
            int finalSaved = totalSaved;
            updateTaskFields(id, task -> {
                task.setStatus("SUCCESS");
                task.setMessage("同步任务完成");
                task.setFinishedAt(TimeUtils.now());
                task.setTotalPulledCount(finalPulled);
                task.setTotalSavedCount(finalSaved);
                task.setProgress(100);
            });

            log.info("[SyncTask] done id={}, pulled={}, saved={}", id, totalPulled, totalSaved);
            rabbitMqSender.sendSyncCompleted(id, dataSourceKey, result.getDatasourceType(),
                    totalPulled, totalSaved);
        } catch (Exception e) {
            updateTaskFields(id, task -> {
                task.setStatus("FAILED");
                task.setMessage("同步任务失败");
                task.setErrorMessage(e.getMessage());
                task.setFinishedAt(TimeUtils.now());
            });
            log.error("[SyncTask] failed id={}", id, e);
        } finally {
            lockTemplate.releaseQuietly(lock);
        }
    }

    /**
     * 更新同步任务的部分字段（查询 → 修改 → 更新，仅修改非 null 字段）。
     *
     * @param taskId  任务 ID
     * @param updater 字段修改回调
     */
    private void updateTaskFields(Long taskId, java.util.function.Consumer<SyncTask> updater) {
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            SyncTask task = syncTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("[SyncTask] 更新失败：任务 {} 不存在", taskId);
                return;
            }
            updater.accept(task);
            syncTaskMapper.updateById(task);
        });
    }
}
