package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.dto.SyncResultDTO;
import com.example.dynamicds.entity.SyncBusinessRecord;
import com.example.dynamicds.entity.SyncTask;
import com.example.dynamicds.mapper.SyncBusinessRecordMapper;
import com.example.dynamicds.mapper.SyncTaskMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
public class SyncTaskService {

    private static final String LOCK_KEY = "risk-hub:sync:task:lock";
    private static final String TAG_SYNC_TASK = "sync_task";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RedissonClient redissonClient;
    private final LeafSegmentService leafSegmentService;
    private final TradeEtlService tradeEtlService;
    private final DynamicDataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;
    private final RabbitMqSender rabbitMqSender;
    private final ThreadPoolExecutor syncTaskExecutor;

    public SyncTaskService(RedissonClient redissonClient,
                           LeafSegmentService leafSegmentService,
                           TradeEtlService tradeEtlService,
                           DynamicDataSourceManager dataSourceManager,
                           RoutingMybatisExecutor routingMybatisExecutor,
                           SyncTaskMapper syncTaskMapper,
                           SyncBusinessRecordMapper syncBusinessRecordMapper,
                           RabbitMqSender rabbitMqSender,
                           @Qualifier("syncTaskExecutor") ThreadPoolExecutor syncTaskExecutor) {
        this.redissonClient = redissonClient;
        this.leafSegmentService = leafSegmentService;
        this.tradeEtlService = tradeEtlService;
        this.dataSourceManager = dataSourceManager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.syncTaskMapper = syncTaskMapper;
        this.syncBusinessRecordMapper = syncBusinessRecordMapper;
        this.rabbitMqSender = rabbitMqSender;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    public SyncTask startTask(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (PlatformBootstrapService.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }

        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            throw new IllegalStateException("已有同步任务正在运行");
        }

        String now = now();
        int safePageSize = Math.max(1, Math.min(pageSize, 500));

        SyncTask task = new SyncTask();
        task.setId(leafSegmentService.nextId(TAG_SYNC_TASK));
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setDataSourceKey(dataSourceKey);
        task.setDataSourceName(config.getName());
        task.setDatasourceType(config.getDatasourceType());
        task.setPageSize(safePageSize);
        task.setSubmittedAt(now);
        task.setMessage("同步任务已提交");
        task.setRunning(true);
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> syncTaskMapper.insert(task));

        log.info("[SyncTask] submit id={}, dataSourceKey={}, pageSize={}", task.getId(), dataSourceKey, safePageSize);
        syncTaskExecutor.submit(() -> runTask(task.getId(), dataSourceKey, safePageSize, config.getName(), config.getDatasourceType(), lock));
        return task;
    }

    public SyncTask currentTask() {
        SyncTask task = routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB, () ->
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

    private void runTask(Long id, String dataSourceKey, int pageSize,
                         String dataSourceName, String datasourceType, RLock lock) {
        try {
            updateTask(id, "RUNNING", now(), null, null, null, 0, 0, 0);

            SyncResultDTO result = tradeEtlService.syncByDataSource(
                    dataSourceKey, pageSize, p -> {});

            int totalPulled = 0;
            int totalSaved = 0;

            for (Map.Entry<String, com.example.dynamicds.sync.SyncSupport.BusinessSyncResult> entry :
                    result.getBusinessResults().entrySet()) {
                String bizCode = entry.getKey();
                com.example.dynamicds.sync.SyncSupport.BusinessSyncResult bizResult = entry.getValue();

                SyncBusinessRecord record = new SyncBusinessRecord();
                record.setTaskId(id);
                record.setBusinessCode(bizCode);
                record.setStatus("SUCCESS");
                record.setPageCount(bizResult.getPageCount());
                record.setPulledCount(bizResult.getPulledCount());
                record.setSavedCount(bizResult.getSavedCount());
                record.setLastRowId(bizResult.getLastRowId());
                record.setStartedAt(now());
                record.setFinishedAt(now());
                routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB,
                        () -> syncBusinessRecordMapper.insert(record));

                totalPulled += bizResult.getPulledCount();
                totalSaved += bizResult.getSavedCount();
            }

            updateTask(id, "SUCCESS", null, "同步任务完成", null, now(),
                    totalPulled, totalSaved, 100);
            log.info("[SyncTask] done id={}, pulled={}, saved={}", id, totalPulled, totalSaved);

            rabbitMqSender.sendSyncCompleted(id, dataSourceKey, datasourceType, totalPulled, totalSaved);
        } catch (Exception e) {
            updateTask(id, "FAILED", null, "同步任务失败", e.getMessage(), now(), 0, 0, 0);
            log.error("[SyncTask] failed id={}", id, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void updateTask(Long id, String status, String startedAt,
                            String message, String errorMessage,
                            String finishedAt, int totalPulled, int totalSaved, Integer progress) {
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> {
            SyncTask task = syncTaskMapper.selectById(id);
            if (task == null) return;
            task.setStatus(status);
            if (startedAt != null) task.setStartedAt(startedAt);
            if (message != null) task.setMessage(message);
            if (errorMessage != null) task.setErrorMessage(errorMessage);
            if (finishedAt != null) task.setFinishedAt(finishedAt);
            task.setTotalPulledCount(totalPulled);
            task.setTotalSavedCount(totalSaved);
            if (progress != null) task.setProgress(progress);
            syncTaskMapper.updateById(task);
        });
    }

    private String now() {
        return LocalDateTime.now().format(FORMATTER);
    }

    @PreDestroy
    public void shutdown() {
        syncTaskExecutor.shutdown();
    }
}
