package com.example.dynamicds.service;

import com.example.dynamicds.dto.DataSourceConfigDTO;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyncTaskService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TradeEtlService tradeEtlService;
    private final com.example.dynamicds.datasource.DynamicDataSourceManager dataSourceManager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "risk-hub-sync-task");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile SyncTaskSnapshot currentTask = SyncTaskSnapshot.idle();

    public Map<String, Object> startTask(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (PlatformBootstrapService.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("当前已有同步任务运行中，同一时间只能有一条");
        }

        SyncTaskSnapshot snapshot = new SyncTaskSnapshot();
        snapshot.setTaskId(UUID.randomUUID().toString());
        snapshot.setStatus("QUEUED");
        snapshot.setDataSourceKey(dataSourceKey);
        snapshot.setDataSourceName(config.getName());
        snapshot.setDatasourceType(config.getDatasourceType());
        snapshot.setPageSize(Math.max(1, Math.min(pageSize, 200)));
        snapshot.setSubmittedAt(now());
        snapshot.setMessage("同步任务已提交，等待后台执行");
        currentTask = snapshot;

        executor.submit(() -> runTask(snapshot));
        return toMap(currentTask);
    }

    public Map<String, Object> currentTask() {
        return toMap(currentTask);
    }

    private void runTask(SyncTaskSnapshot snapshot) {
        try {
            snapshot.setStatus("RUNNING");
            snapshot.setStartedAt(now());
            snapshot.setMessage("同步任务执行中");
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
            snapshot.setMessage("同步任务执行完成");
            currentTask = snapshot;
            log.info("[同步任务] 执行完成 taskId={}, savedCount={}", snapshot.getTaskId(), snapshot.getSavedCount());
        } catch (Exception e) {
            snapshot.setStatus("FAILED");
            snapshot.setFinishedAt(now());
            snapshot.setErrorMessage(e.getMessage());
            snapshot.setMessage("同步任务执行失败");
            currentTask = snapshot;
            log.error("[同步任务] 执行失败 taskId={}, message={}", snapshot.getTaskId(), e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    private void updateProgress(SyncTaskSnapshot snapshot, TradeEtlService.SyncProgress progress) {
        snapshot.setPageCount(progress.getPageNo());
        snapshot.setPulledCount(progress.getPulledCount());
        snapshot.setSavedCount(progress.getSavedCount());
        snapshot.setLastRowId(progress.getLastRowId());
        snapshot.setMessage("同步任务执行中，第 " + progress.getPageNo() + " 页已完成");
        currentTask = snapshot;
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
        result.put("result", snapshot.getResult());
        return result;
    }

    private String now() {
        return LocalDateTime.now().format(FORMATTER);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
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
        private Map<String, Object> result;

        private static SyncTaskSnapshot idle() {
            SyncTaskSnapshot snapshot = new SyncTaskSnapshot();
            snapshot.setStatus("IDLE");
            snapshot.setMessage("当前没有运行中的同步任务");
            return snapshot;
        }
    }
}
