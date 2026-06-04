package com.mlm.pipeline.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.StepStatus;
import com.mlm.model.config.ModelConfigEntity;
import com.mlm.model.config.ModelConfigLoader;
import com.mlm.model.core.ModelGateway;
import com.mlm.pipeline.engine.PipelineEngine;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Task;
import com.mlm.pipeline.mapper.EpisodeMapper;
import com.mlm.pipeline.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 定时轮询调度器 — 双重职责
 * <p>
 * <ol>
 *   <li><b>轮询 AI 任务</b> — 扫描 PROCESSING 状态的任务，调用厂商接口查询进度</li>
 *   <li><b>推进就绪步骤</b> — 当某步骤的所有 AI 任务都成功完成时，自动推进剧集到下一状态</li>
 * </ol>
 * <p>
 * 职责分离：轮询由 {@link ModelGateway} 处理，推进由 {@link PipelineEngine} 处理，
 * 此调度器只做编排（Orchestration），不嵌入业务逻辑。
 * <p>
 * 【自动推进条件】
 * 剧集处于 STORYBOARD 或 GENERATING 状态，且 stepStatus == SUCCESS（全部任务完成）
 * → 调用 PipelineEngine.advance() 推进到下一状态。
 * 这是串联「AI 异步完成」与「状态机推进」的关键枢纽。
 */
@Component
public class TaskPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskPollingScheduler.class);

    /** AI 步骤完成后需要自动推进的剧集状态集合 */
    private static final List<EpisodeStatus> AUTO_ADVANCE_STATES = java.util.Arrays.asList(
        EpisodeStatus.STORYBOARD,
        EpisodeStatus.GENERATING
    );

    private final TaskService taskService;
    private final ModelGateway modelGateway;
    private final ModelConfigLoader configLoader;
    private final PipelineEngine pipelineEngine;
    private final EpisodeMapper episodeMapper;

    public TaskPollingScheduler(TaskService taskService,
                                ModelGateway modelGateway,
                                ModelConfigLoader configLoader,
                                PipelineEngine pipelineEngine,
                                EpisodeMapper episodeMapper) {
        this.taskService = taskService;
        this.modelGateway = modelGateway;
        this.configLoader = configLoader;
        this.pipelineEngine = pipelineEngine;
        this.episodeMapper = episodeMapper;
    }

    /** 每 30 秒扫描一次 */
    @Scheduled(fixedDelay = 30_000)
    public void pollAndAdvance() {
        // ---- 职责 1：轮询 AI 任务 ----
        List<Task> tasks = taskService.findProcessingAndReadyToPoll();

        for (Task task : tasks) {
            try {
                Optional<ModelConfigEntity> configOpt = configLoader.load(task.getVendor(), task.getModelType());
                if (!configOpt.isPresent()) {
                    log.warn("未找到模型配置: vendor={}, type={}", task.getVendor(), task.getModelType());
                    continue;
                }
                modelGateway.pollAndUpdate(task, configOpt.get());
            } catch (Exception e) {
                log.error("轮询任务异常: taskId={}", task.getId(), e);
            }
        }

        // ---- 职责 2：推进就绪步骤 ----
        for (EpisodeStatus state : AUTO_ADVANCE_STATES) {
            List<Episode> readyEpisodes = episodeMapper.selectList(
                new LambdaQueryWrapper<Episode>()
                    .eq(Episode::getStatus, state)
                    .eq(Episode::getStepStatus, StepStatus.SUCCESS)
            );
            for (Episode episode : readyEpisodes) {
                try {
                    log.info("步骤完成，自动推进: episodeId={}, state={}", episode.getId(), state);
                    pipelineEngine.advance(episode);
                } catch (Exception e) {
                    log.error("自动推进失败: episodeId={}, state={}", episode.getId(), state, e);
                }
            }
        }
    }
}
