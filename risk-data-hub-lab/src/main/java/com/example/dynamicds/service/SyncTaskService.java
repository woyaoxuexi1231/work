package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.dto.SyncResultDTO;
import com.example.dynamicds.dto.SyncTaskDTO;
import com.example.dynamicds.entity.SyncBusinessRecord;
import com.example.dynamicds.entity.SyncTask;
import com.example.dynamicds.mapper.SyncBusinessRecordMapper;
import com.example.dynamicds.mapper.SyncTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 同步任务服务 — 异步执行 ETL 同步，任务及业务记录持久化到数据库。
 * <p>
 * 流程：创建 sync_task → 插入 sync_business_record（RUNNING）→
 * 执行同步 → 更新 sync_business_record（SUCCESS/FAILED）→
 * 更新 sync_task → 全部成功则发 RabbitMQ 消息。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SyncTaskService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger currentProgress = new AtomicInteger(0);

    private final TradeEtlService tradeEtlService;
    private final DynamicDataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;
    private final RabbitMqSender rabbitMqSender;
    @Qualifier("syncTaskExecutor")
    private final ThreadPoolExecutor syncTaskExecutor;

    /**
     * 提交同步任务，写入 sync_task 表。
     */
    public SyncTaskDTO startTask(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (PlatformBootstrapService.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("已有同步任务正在运行");
        }
        currentProgress.set(0);

        String taskId = UUID.randomUUID().toString();
        String now = now();
        int safePageSize = Math.max(1, Math.min(pageSize, 500));

        SyncTask task = new SyncTask();
        task.setTaskId(taskId);
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setDataSourceKey(dataSourceKey);
        task.setDataSourceName(config.getName());
        task.setDatasourceType(config.getDatasourceType());
        task.setPageSize(safePageSize);
        task.setSubmittedAt(now);
        task.setMessage("同步任务已提交");
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> syncTaskMapper.insert(task));

        log.info("[同步任务] 提交 taskId={}, dataSourceKey={}, pageSize={}", taskId, dataSourceKey, safePageSize);
        syncTaskExecutor.submit(() -> runTask(taskId, dataSourceKey, safePageSize, config.getName(), config.getDatasourceType()));
        return toDTO(task);
    }

    /**
     * 查询最新的同步任务（按 id 降序取第一条）。
     */
    public SyncTaskDTO currentTask() {
        SyncTask task = routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB, () ->
                syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                        .orderByDesc(SyncTask::getId)
                        .last("limit 1")));
        if (task == null) {
            SyncTask idle = new SyncTask();
            idle.setStatus("IDLE");
            idle.setProgress(0);
            idle.setMessage("暂无同步任务");
            return toDTO(idle);
        }
        // 同步内存中的进度到数据库
        if ("RUNNING".equals(task.getStatus())) {
            task.setProgress(currentProgress.get());
        }
        return toDTO(task);
    }

    private void runTask(String taskId, String dataSourceKey, int pageSize,
                         String dataSourceName, String datasourceType) {
        try {
            // → RUNNING
            updateTask(taskId, "RUNNING", now(), null, null, null, null, 0, 0, 0);

            // 执行同步（带进度回调）
            SyncResultDTO result = tradeEtlService.syncByDataSource(
                    dataSourceKey, pageSize, currentProgress::set);

            // 解析业务结果并写入 sync_business_record
            int totalPulled = 0;
            int totalSaved = 0;

            for (Map.Entry<String, com.example.dynamicds.sync.SyncSupport.BusinessSyncResult> entry :
                    result.getBusinessResults().entrySet()) {
                String bizCode = entry.getKey();
                com.example.dynamicds.sync.SyncSupport.BusinessSyncResult bizResult = entry.getValue();

                // 写入业务同步记录
                SyncBusinessRecord record = new SyncBusinessRecord();
                record.setTaskId(taskId);
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

            // → SUCCESS
            String resultJson = toJson(result);
            updateTask(taskId, "SUCCESS", null, resultJson, "同步任务完成", null, now(),
                    totalPulled, totalSaved, 100);
            log.info("[同步任务] 执行完成 taskId={}, 拉取总数={}, 落库总数={}", taskId, totalPulled, totalSaved);

            // 发 RabbitMQ 消息
            rabbitMqSender.sendSyncCompleted(taskId, dataSourceKey, datasourceType, totalPulled, totalSaved);
        } catch (Exception e) {
            updateTask(taskId, "FAILED", null, null, "同步任务失败", e.getMessage(), now(), 0, 0, currentProgress.get());
            log.error("[同步任务] 执行失败 taskId={}", taskId, e);
        } finally {
            running.set(false);
        }
    }

    private void updateTask(String taskId, String status, String startedAt,
                            String resultJson, String message, String errorMessage,
                            String finishedAt, int totalPulled, int totalSaved, Integer progress) {
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> {
            SyncTask task = syncTaskMapper.selectOne(
                    new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getTaskId, taskId).last("limit 1"));
            if (task == null) return;
            task.setStatus(status);
            if (startedAt != null) task.setStartedAt(startedAt);
            if (resultJson != null) task.setResult(resultJson);
            if (message != null) task.setMessage(message);
            if (errorMessage != null) task.setErrorMessage(errorMessage);
            if (finishedAt != null) task.setFinishedAt(finishedAt);
            task.setTotalPulledCount(totalPulled);
            task.setTotalSavedCount(totalSaved);
            if (progress != null) task.setProgress(progress);
            syncTaskMapper.updateById(task);
        });
    }

    private SyncTaskDTO toDTO(SyncTask task) {
        boolean isRunning = "QUEUED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus());
        return new SyncTaskDTO(
                task.getTaskId(),
                task.getStatus(),
                task.getProgress() != null ? task.getProgress() : 0,
                task.getDataSourceKey(),
                task.getDataSourceName(),
                task.getDatasourceType(),
                task.getPageSize(),
                task.getTotalPulledCount(),
                task.getTotalSavedCount(),
                task.getSubmittedAt(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getMessage(),
                task.getErrorMessage(),
                isRunning
        );
    }

    private String toJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String now() {
        return LocalDateTime.now().format(FORMATTER);
    }

    @PreDestroy
    public void shutdown() {
        syncTaskExecutor.shutdown();
    }
}
