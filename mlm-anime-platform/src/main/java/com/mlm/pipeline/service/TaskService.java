package com.mlm.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.enums.StepStatus;
import com.mlm.pipeline.entity.Task;
import com.mlm.pipeline.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务服务 — AI 生成任务的 CRUD + 轮询查询
 * <p>
 * 任务关联到剧集 (episode) 而非项目，属于某集某步骤的 AI 调用。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskMapper taskMapper;

    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /** 创建新任务 */
    public Task create(Task task) {
        taskMapper.insert(task);
        log.debug("任务已创建: id={}, episodeId={}, step={}", task.getId(), task.getEpisodeId(), task.getStep());
        return task;
    }

    /** 新增或更新 */
    public void save(Task task) {
        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
    }

    /** 查询某剧集某步骤下的所有任务 */
    public List<Task> findByEpisodeAndStep(Long episodeId, String step) {
        return taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getEpisodeId, episodeId)
                .eq(Task::getStep, step)
        );
    }

    /** 按任务子状态查询 */
    public List<Task> findByStatus(StepStatus status) {
        return taskMapper.selectList(
            new LambdaQueryWrapper<Task>().eq(Task::getStatus, status)
        );
    }

    /** 查询所有 PROCESSING 且已到轮询时间的任务 */
    public List<Task> findProcessingAndReadyToPoll() {
        return taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, StepStatus.PROCESSING)
                .le(Task::getNextPollAt, LocalDateTime.now())
        );
    }
}
