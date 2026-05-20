package com.example.dynamicds.service;

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
 * 初始化任务服务 — 异步执行平台演示数据初始化。
 * <p>
 * <b>与 SyncTaskService 相同的设计模式</b>
 * <ol>
 *   <li><b>AtomicBoolean CAS 锁</b> — 防止前端连续点击按钮导致并发初始化。</li>
 *   <li><b>volatile 快照</b> — 前端轮询读到的永远是完整对象，不会读到"写了一半"的数据。</li>
 *   <li><b>不可变快照模式（snapshot replacement）</b> — 每个状态更新都是替换引用而非修改字段。</li>
 * </ol>
 * <p>
 * <b>为什么初始化也要异步？</b><br>
 * 初始化需要通过 Marketstack API（或本地兜底）拉取股票数据并写入三个数据库，
 * 耗时可能几十秒到几分钟。如果在 HTTP 请求线程中同步执行，
 * 前端会一直等待超时（浏览器或网关通常 30 秒超时）。
 * 异步任务 + 轮询模式是 Web 应用中处理长时间操作的经典方案。
 */
@Service
@Slf4j
public class InitDataTaskService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** CAS 锁 — 保证同一时刻只有一个初始化任务在运行 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** volatile 快照 — 前端轮询 init-task 接口时直接读取此对象 */
    private volatile InitTaskSnapshot currentTask = InitTaskSnapshot.idle();

    private final PlatformBootstrapService platformBootstrapService;
    private final ThreadPoolExecutor initDataTaskExecutor;

    public InitDataTaskService(
            PlatformBootstrapService platformBootstrapService,
            @Qualifier("initDataTaskExecutor") ThreadPoolExecutor initDataTaskExecutor) {
        this.platformBootstrapService = platformBootstrapService;
        this.initDataTaskExecutor = initDataTaskExecutor;
    }

    /**
     * 提交初始化任务（幂等：已有运行中的任务则拒绝）
     */
    public Map<String, Object> startTask() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("已有初始化任务正在运行");
        }

        InitTaskSnapshot snapshot = new InitTaskSnapshot();
        snapshot.setTaskId(UUID.randomUUID().toString());
        snapshot.setStatus("QUEUED");
        snapshot.setSubmittedAt(now());
        snapshot.setMessage("Init task submitted");
        currentTask = snapshot;
        log.info("[InitTask] submitted taskId={}", snapshot.getTaskId());

        initDataTaskExecutor.submit(() -> runTask(snapshot));
        return toMap(snapshot);
    }

    public Map<String, Object> currentTask() {
        return toMap(currentTask);
    }

    private void runTask(InitTaskSnapshot snapshot) {
        try {
            snapshot.setStatus("RUNNING");
            snapshot.setStartedAt(now());
            snapshot.setMessage("Init task is running");
            currentTask = snapshot;
            log.info("[InitTask] started taskId={}", snapshot.getTaskId());

            Map<String, Object> result = platformBootstrapService.initDemoData();
            snapshot.setStatus("SUCCESS");
            snapshot.setFinishedAt(now());
            snapshot.setMessage("Init task finished");
            snapshot.setResult(result);
            currentTask = snapshot;
            log.info("[InitTask] finished taskId={}, snapshotCount={}",
                    snapshot.getTaskId(), result.getOrDefault("snapshotCount", 0));
        } catch (Exception e) {
            snapshot.setStatus("FAILED");
            snapshot.setFinishedAt(now());
            snapshot.setMessage("Init task failed");
            snapshot.setErrorMessage(e.getMessage());
            currentTask = snapshot;
            log.error("[InitTask] failed taskId={}, message={}", snapshot.getTaskId(), e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    private Map<String, Object> toMap(InitTaskSnapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", snapshot.getTaskId());
        result.put("status", snapshot.getStatus());
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
        initDataTaskExecutor.shutdown();
    }

    @Data
    private static class InitTaskSnapshot {
        private String taskId;
        private String status;
        private String submittedAt;
        private String startedAt;
        private String finishedAt;
        private String message;
        private String errorMessage;
        private Map<String, Object> result;

        private static InitTaskSnapshot idle() {
            InitTaskSnapshot snapshot = new InitTaskSnapshot();
            snapshot.setStatus("IDLE");
            snapshot.setMessage("No init task is running");
            return snapshot;
        }
    }
}