package com.example.dynamicds.service;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.sync.SyncSupport.SyncProgress;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 同步任务服务 — 异步执行 ETL 同步（4 类业务并发）。
 * 使用独立的单线程池（syncTaskExecutor）顺序执行，
 * 保证同一时刻只有一个同步任务在运行。
 * 前端 POST /api/hub/sync 触发，通过轮询 /api/hub/sync-task 获取进度。
 */
@Service
@Slf4j
public class SyncTaskService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SyncTaskSnapshot currentTask = SyncTaskSnapshot.idle();

    private final TradeEtlService tradeEtlService;
    private final DynamicDataSourceManager dataSourceManager;
    private final ThreadPoolExecutor syncTaskExecutor;

    public SyncTaskService(TradeEtlService tradeEtlService,
                           DynamicDataSourceManager dataSourceManager,
                           @Qualifier("syncTaskExecutor") ThreadPoolExecutor syncTaskExecutor) {
        this.tradeEtlService = tradeEtlService;
        this.dataSourceManager = dataSourceManager;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    public Map<String, Object> startTask(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("Data source not found: " + dataSourceKey);
        }
        if (PlatformBootstrapService.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("Hub datasource cannot be used as sync source: " + dataSourceKey);
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("已有同步任务正在运行");
        }

        SyncTaskSnapshot snapshot = new SyncTaskSnapshot();
        snapshot.setTaskId(UUID.randomUUID().toString());
        snapshot.setStatus("QUEUED");
        snapshot.setDataSourceKey(dataSourceKey);
        snapshot.setDataSourceName(config.getName());
        snapshot.setDatasourceType(config.getDatasourceType());
        snapshot.setPageSize(Math.max(1, Math.min(pageSize, 500)));
        snapshot.setSubmittedAt(now());
        snapshot.setMessage("Sync task submitted");
        currentTask = snapshot;

        log.info("[同步任务] 提交 taskId={}, dataSourceKey={}, pageSize={}",
                snapshot.getTaskId(), snapshot.getDataSourceKey(), snapshot.getPageSize());
        syncTaskExecutor.submit(() -> runTask(snapshot));
        return toMap(currentTask);
    }

    public Map<String, Object> currentTask() {
        return toMap(currentTask);
    }

    private void runTask(SyncTaskSnapshot snapshot) {
        try {
            snapshot.setStatus("RUNNING");
            snapshot.setStartedAt(now());
            snapshot.setMessage("Sync task is running");
            currentTask = snapshot;
            log.info("[同步任务] 开始执行 taskId={}, dataSourceKey={}, pageSize={}",
                    snapshot.getTaskId(), snapshot.getDataSourceKey(), snapshot.getPageSize());

            Map<String, Object> result = tradeEtlService.syncByDataSource(
                    snapshot.getDataSourceKey(),
                    snapshot.getPageSize(),
                    progress -> updateProgress(snapshot, progress));

            snapshot.setStatus("SUCCESS");
            snapshot.setFinishedAt(now());
            snapshot.setResult(result);
            snapshot.setMessage("Sync task finished");
            currentTask = snapshot;
            log.info("[同步任务] 执行完成 taskId={}, 落库总数={}", snapshot.getTaskId(), snapshot.getSavedCount());
        } catch (Exception e) {
            snapshot.setStatus("FAILED");
            snapshot.setFinishedAt(now());
            snapshot.setErrorMessage(e.getMessage());
            snapshot.setMessage("Sync task failed");
            currentTask = snapshot;
            log.error("[同步任务] 执行失败 taskId={}, 错误={}", snapshot.getTaskId(), e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    private void updateProgress(SyncTaskSnapshot snapshot, SyncProgress progress) {
        snapshot.getBusinessProgress().put(progress.getBusinessCode(), progress.toMap());
        int totalPageCount = 0;
        int totalPulledCount = 0;
        int totalSavedCount = 0;
        for (Object value : snapshot.getBusinessProgress().values()) {
            if (!(value instanceof Map<?, ?> business)) {
                continue;
            }
            totalPageCount += readInt(business.get("pageNo"));
            totalPulledCount += readInt(business.get("pulledCount"));
            totalSavedCount += readInt(business.get("savedCount"));
        }
        snapshot.setPageCount(totalPageCount);
        snapshot.setPulledCount(totalPulledCount);
        snapshot.setSavedCount(totalSavedCount);
        snapshot.setLastRowId(progress.getLastRowId());
        snapshot.setMessage("Running " + progress.getBusinessCode() + " stage=" + progress.getStage());
        currentTask = snapshot;
    }

    private int readInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private Map<String, Object> toMap(SyncTaskSnapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", snapshot.getTaskId());
        result.put("status", snapshot.getStatus());
        result.put("dataSourceKey", snapshot.getDataSourceKey());
        result.put("dataSourceName", snapshot.getDataSourceName());
        result.put("datasourceType", snapshot.getDatasourceType());
        result.put("pageSize", snapshot.getPageSize());
        result.put("pageCount", snapshot.getPageCount());
        result.put("pulledCount", snapshot.getPulledCount());
        result.put("savedCount", snapshot.getSavedCount());
        result.put("lastRowId", snapshot.getLastRowId());
        result.put("submittedAt", snapshot.getSubmittedAt());
        result.put("startedAt", snapshot.getStartedAt());
        result.put("finishedAt", snapshot.getFinishedAt());
        result.put("message", snapshot.getMessage());
        result.put("errorMessage", snapshot.getErrorMessage());
        result.put("running", "QUEUED".equals(snapshot.getStatus()) || "RUNNING".equals(snapshot.getStatus()));
        result.put("businessProgress", snapshot.getBusinessProgress());
        result.put("result", snapshot.getResult());
        return result;
    }

    private String now() {
        return LocalDateTime.now().format(FORMATTER);
    }

    @PreDestroy
    public void shutdown() {
        syncTaskExecutor.shutdown();
    }

    @Data
    private static class SyncTaskSnapshot {
        private String taskId;
        private String status;
        private String dataSourceKey;
        private String dataSourceName;
        private String datasourceType;
        private int pageSize;
        private int pageCount;
        private int pulledCount;
        private int savedCount;
        private long lastRowId;
        private String submittedAt;
        private String startedAt;
        private String finishedAt;
        private String message;
        private String errorMessage;
        private Map<String, Object> businessProgress = new LinkedHashMap<>();
        private Map<String, Object> result;

        private static SyncTaskSnapshot idle() {
            SyncTaskSnapshot snapshot = new SyncTaskSnapshot();
            snapshot.setStatus("IDLE");
            snapshot.setMessage("No sync task is running");
            return snapshot;
        }
    }
}