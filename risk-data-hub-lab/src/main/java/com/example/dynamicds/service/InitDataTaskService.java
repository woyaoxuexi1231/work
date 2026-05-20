package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.InitTask;
import com.example.dynamicds.mapper.InitTaskMapper;
import jakarta.annotation.PreDestroy;
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
 * 任务信息持久化到 {@code init_task} 表，替代原内存快照模式。
 * 前端通过查询数据库获取最新任务状态。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InitDataTaskService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlatformBootstrapService platformBootstrapService;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final InitTaskMapper initTaskMapper;
    @Qualifier("initDataTaskExecutor")
    private final ThreadPoolExecutor initDataTaskExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 提交初始化任务，记录到 init_task 表。
     */
    public Map<String, Object> startTask() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("已有初始化任务正在运行");
        }

        String taskId = UUID.randomUUID().toString();
        String now = now();

        // 插入任务记录（QUEUED）
        InitTask task = new InitTask();
        task.setTaskId(taskId);
        task.setStatus("QUEUED");
        task.setSubmittedAt(now);
        task.setMessage("初始化任务已提交");
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> initTaskMapper.insert(task));

        log.info("[InitTask] 提交初始化任务 taskId={}", taskId);
        initDataTaskExecutor.submit(() -> runTask(taskId));
        return toMap(task);
    }

    /**
     * 查询最新的初始化任务（按 id 降序取第一条）。
     */
    public Map<String, Object> currentTask() {
        InitTask task = routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB, () ->
                initTaskMapper.selectOne(new LambdaQueryWrapper<InitTask>()
                        .orderByDesc(InitTask::getId)
                        .last("limit 1")));
        if (task == null) {
            InitTask idle = new InitTask();
            idle.setStatus("IDLE");
            idle.setMessage("暂无初始化任务");
            return toMap(idle);
        }
        return toMap(task);
    }

    private void runTask(String taskId) {
        try {
            // 更新为 RUNNING
            updateTask(taskId, "RUNNING", null, "初始化任务执行中...", null, now(), null);

            Map<String, Object> result = platformBootstrapService.initDemoData();
            int snapshotCount = ((Number) result.getOrDefault("snapshotCount", 0)).intValue();

            // 更新为 SUCCESS
            updateTask(taskId, "SUCCESS", null, "初始化任务完成", null, null, now());
            log.info("[InitTask] 初始化完成 taskId={}, 股票条数={}", taskId, snapshotCount);
        } catch (Exception e) {
            updateTask(taskId, "FAILED", null, "初始化任务失败", e.getMessage(), null, now());
            log.error("[InitTask] 初始化失败 taskId={}", taskId, e);
        } finally {
            running.set(false);
        }
    }

    private void updateTask(String taskId, String status, String resultJson,
                            String message, String errorMessage,
                            String startedAt, String finishedAt) {
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> {
            InitTask task = initTaskMapper.selectOne(
                    new LambdaQueryWrapper<InitTask>().eq(InitTask::getTaskId, taskId).last("limit 1"));
            if (task == null) return;
            task.setStatus(status);
            if (resultJson != null) task.setResult(resultJson);
            if (message != null) task.setMessage(message);
            if (errorMessage != null) task.setErrorMessage(errorMessage);
            if (startedAt != null) task.setStartedAt(startedAt);
            if (finishedAt != null) task.setFinishedAt(finishedAt);
            initTaskMapper.updateById(task);
        });
    }

    private Map<String, Object> toMap(InitTask task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("status", task.getStatus());
        result.put("submittedAt", task.getSubmittedAt());
        result.put("startedAt", task.getStartedAt());
        result.put("finishedAt", task.getFinishedAt());
        result.put("message", task.getMessage());
        result.put("errorMessage", task.getErrorMessage());
        result.put("running", "QUEUED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()));
        return result;
    }

    private String now() {
        return LocalDateTime.now().format(FORMATTER);
    }

    @PreDestroy
    public void shutdown() {
        initDataTaskExecutor.shutdown();
    }
}
