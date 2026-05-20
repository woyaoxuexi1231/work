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
 * <p>
 * <b>AtomicBoolean + volatile 的设计</b>
 * <ul>
 *   <li><b>running（AtomicBoolean）</b> — 防止并发提交同步任务。
 *       {@code compareAndSet(false, true)} 是原子操作，保证即使两个请求同时到达，
 *       也只有一个能成功设置 running=true。</li>
 *   <li><b>currentTask（volatile）</b> — 前端轮询的"快照"对象。
 *       volatile 保证一个线程（工作线程）修改了 currentTask 的引用，
 *       另一个线程（前端请求线程）立即可见。
 *       为什么不用 {@code final + synchronized}？因为 currentTask 是"替换"而不是"修改"：
 *       工作线程创建新的 SyncTaskSnapshot 对象来替换旧对象，
 *       volatile 保证了"新对象引用"对所有线程的可见性。</li>
 * </ul>
 * <p>
 * <b>为什么用独立的单线程池（syncTaskExecutor）而不是直接用 @Async？</b>
 * <ol>
 *   <li>@Async 默认使用 Spring 的全局线程池，如果在其他地方也用 @Async，
 *       可能会相互干扰。独立线程池职责明确。</li>
 *   <li>队列容量（8）预留了缓冲，即使任务提交略快于执行也不会丢任务。</li>
 *   <li>{@code destroyMethod = "shutdown"} 保证应用关闭时优雅终止。</li>
 * </ol>
 * <p>
 * <b>不可变快照模式</b><br>
 * SyncTaskSnapshot 每次更新时创建新对象替换旧对象（currentTask = snapshot），
 * 而不是修改已有对象的字段。这样前端轮询读取 currentTask 时，
 * 要么读到完整的旧快照，要么读到完整的新快照，不会读到"半修改"的对象。
 * 如果直接修改 snapshot 的字段，前端可能读到 status=SUCCESS 但 result=null 的中间状态。
 */
@Service
@Slf4j
public class SyncTaskService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** CAS 锁 — 保证同一时刻只有一个同步任务在运行 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** volatile 快照 — 前端轮询读到的一直是完整对象 */
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