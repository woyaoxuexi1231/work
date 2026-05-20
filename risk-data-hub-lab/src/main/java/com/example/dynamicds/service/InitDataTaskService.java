package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.InitTask;
import com.example.dynamicds.mapper.InitTaskMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class InitDataTaskService {

    private static final String LOCK_KEY = "risk-hub:init:task:lock";
    private static final String TAG_INIT_TASK = "init_task";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RedissonClient redissonClient;
    private final LeafSegmentService leafSegmentService;
    private final PlatformBootstrapService platformBootstrapService;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final InitTaskMapper initTaskMapper;
    @Qualifier("initDataTaskExecutor")
    private final ThreadPoolExecutor initDataTaskExecutor;

    public InitTask startTask() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            throw new IllegalStateException("已有初始化任务正在运行");
        }

        String now = now();
        InitTask task = new InitTask();
        task.setId(leafSegmentService.nextId(TAG_INIT_TASK));
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setSubmittedAt(now);
        task.setMessage("初始化任务已提交");
        task.setRunning(true);
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> initTaskMapper.insert(task));

        log.info("[InitTask] submit id={}", task.getId());
        initDataTaskExecutor.submit(() -> runTask(task.getId(), lock));
        return task;
    }

    public InitTask currentTask() {
        InitTask task = routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB, () ->
                initTaskMapper.selectOne(new LambdaQueryWrapper<InitTask>()
                        .orderByDesc(InitTask::getId)
                        .last("limit 1")));
        if (task == null) {
            InitTask idle = new InitTask();
            idle.setStatus("IDLE");
            idle.setProgress(0);
            idle.setMessage("暂无初始化任务");
            idle.setRunning(false);
            return idle;
        }
        task.setRunning("QUEUED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()));
        return task;
    }

    private void runTask(Long id, RLock lock) {
        try {
            updateTask(id, "RUNNING", null, 0, "初始化任务执行中...", null, null, now());

            Map<String, Object> result = platformBootstrapService.initDemoDataWithProgress(p -> {});
            int snapshotCount = ((Number) result.getOrDefault("snapshotCount", 0)).intValue();

            updateTask(id, "SUCCESS", null, 100, "初始化任务完成", null, null, now());
            log.info("[InitTask] done id={}, snapshotCount={}", id, snapshotCount);
        } catch (Exception e) {
            updateTask(id, "FAILED", null, 0, "初始化任务失败", e.getMessage(), null, now());
            log.error("[InitTask] failed id={}", id, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void updateTask(Long id, String status, String resultJson, Integer progress,
                            String message, String errorMessage,
                            String startedAt, String finishedAt) {
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> {
            InitTask task = initTaskMapper.selectById(id);
            if (task == null) return;
            task.setStatus(status);
            if (resultJson != null) task.setResult(resultJson);
            if (progress != null) task.setProgress(progress);
            if (message != null) task.setMessage(message);
            if (errorMessage != null) task.setErrorMessage(errorMessage);
            if (startedAt != null) task.setStartedAt(startedAt);
            if (finishedAt != null) task.setFinishedAt(finishedAt);
            initTaskMapper.updateById(task);
        });
    }

    private String now() {
        return LocalDateTime.now().format(FORMATTER);
    }

    @PreDestroy
    public void shutdown() {
        initDataTaskExecutor.shutdown();
    }
}
