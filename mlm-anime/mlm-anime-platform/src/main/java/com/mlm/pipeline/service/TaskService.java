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
 * 任务服务 — AI 生成任务的 CRUD 及轮询查询
 * <p>
 * 【职责】
 * <ul>
 *   <li>任务的创建和更新</li>
 *   <li>按剧集+步骤查询任务列表（用于步骤完成检查）</li>
 *   <li>按子状态查询任务</li>
 *   <li>查询待轮询任务（PROCESSING + 已到轮询时间）</li>
 * </ul>
 * <p>
 * 任务关联到剧集（episode）而非项目，属于某集某步骤的 AI 调用。
 * 任务的生命周期由 {@link com.mlm.model.core.ModelGateway} 管理。
 *
 * @author mlm
 * @see Task 任务实体
 * @see com.mlm.model.core.ModelGateway AI 模型网关
 * @see com.mlm.pipeline.scheduler.TaskPollingScheduler 定时轮询调度器
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskMapper taskMapper;

    /**
     * 构造任务服务
     *
     * @param taskMapper 任务 Mapper
     */
    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 创建新任务
     *
     * @param task 待创建的任务实体
     * @return 创建后的任务实体（含自增 id）
     */
    public Task create(Task task) {
        taskMapper.insert(task);
        log.debug("任务已创建: id={}, episodeId={}, step={}, vendor={}",
                task.getId(), task.getEpisodeId(), task.getStep(), task.getVendor());
        return task;
    }

    /**
     * 新增或更新任务（按 id 是否存在自动判断）
     *
     * @param task 任务实体
     */
    public void save(Task task) {
        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
    }

    /**
     * 查询某剧集某步骤下的所有任务
     * <p>
     * 用于 {@link com.mlm.model.core.ModelGateway#checkStepCompletion}
     * 检查步骤是否全部完成。
     *
     * @param episodeId 剧集 ID
     * @param step      步骤标识（如 "文生图"、"图生视频"）
     * @return 任务列表
     */
    public List<Task> findByEpisodeAndStep(Long episodeId, String step) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getEpisodeId, episodeId)
                        .eq(Task::getStep, step)
        );
    }

    /**
     * 按任务子状态查询
     *
     * @param status 子状态枚举
     * @return 任务列表
     */
    public List<Task> findByStatus(StepStatus status) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getStatus, status)
        );
    }

    /**
     * 查询所有 PROCESSING 状态且已到轮询时间的任务
     * <p>
     * 用于 {@link com.mlm.pipeline.scheduler.TaskPollingScheduler}
     * 定时扫描并轮询厂商 API。
     *
     * @return 待轮询的任务列表
     */
    public List<Task> findProcessingAndReadyToPoll() {
        return taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getStatus, StepStatus.PROCESSING)
                        .le(Task::getNextPollAt, LocalDateTime.now())
        );
    }
}
