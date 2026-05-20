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
 * 使用独立的单线程池（initDataTaskExecutor）顺序执行，
 * 防止并发初始化导致数据冲突。
 * 前端 POST /api/hub/init-data 触发，通过轮询 /api/hub/init-task 获取进度。
 */
@Service
@Slf4j
public class InitDataTaskService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final AtomicBoolean running = new AtomicBoolean(false);
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