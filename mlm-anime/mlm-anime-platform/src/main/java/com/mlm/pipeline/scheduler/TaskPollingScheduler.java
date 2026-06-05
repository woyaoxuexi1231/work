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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 定时轮询调度器 — AI 任务轮询 + 就绪步骤自动推进
 * <p>
 * 【双重职责】
 * <ol>
 *   <li><b>AI 任务轮询</b> — 扫描 PROCESSING 状态且已到轮询时间的任务，
 *       调用对应厂商接口查询进度并更新任务状态</li>
 *   <li><b>就绪步骤推进</b> — 当 STORYBOARD 或 GENERATING 步骤的所有
 *       AI 任务均成功完成时，自动调用 {@link PipelineEngine#advance}
 *       将剧集推进到下一状态</li>
 * </ol>
 * <p>
 * 【职责分离】
 * <ul>
 *   <li>轮询逻辑 — {@link ModelGateway#pollAndUpdate(Task, ModelConfigEntity)}</li>
 *   <li>状态推进 — {@link PipelineEngine#advance(Episode)}</li>
 *   <li>此调度器只做编排（Orchestration），不嵌入业务逻辑</li>
 * </ul>
 * <p>
 * 【自动推进枢纽】
 * 这是串联「AI 异步完成」与「状态机推进」的关键枢纽。
 * AI 任务完成后 stepStatus 被置为 SUCCESS，调度器扫描到后触发推进。
 * <p>
 * 【执行频率】
 * 每 30 秒执行一次（{@code fixedDelay = 30_000}），确保轮询和推进的及时性
 * 同时避免对数据库造成过大压力。
 *
 * @author mlm
 * @see ModelGateway
 * @see PipelineEngine
 */
@Component
public class TaskPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskPollingScheduler.class);

    /**
     * AI 步骤完成后需要自动推进的剧集状态集合
     * <p>
     * 当这些状态的 stepStatus 变为 SUCCESS（所有 AI 任务完成）时，
     * 调度器自动调用 advance() 推进到下一状态。
     */
    private static final List<EpisodeStatus> AUTO_ADVANCE_STATES = Arrays.asList(
            EpisodeStatus.STORYBOARD,
            EpisodeStatus.GENERATING
    );

    private final TaskService taskService;
    private final ModelGateway modelGateway;
    private final ModelConfigLoader configLoader;
    private final PipelineEngine pipelineEngine;
    private final EpisodeMapper episodeMapper;

    /**
     * 构造定时轮询调度器
     *
     * @param taskService    任务服务
     * @param modelGateway   AI 模型网关（执行轮询）
     * @param configLoader   模型配置加载器
     * @param pipelineEngine Pipeline 引擎（执行推进）
     * @param episodeMapper  剧集 Mapper
     */
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

    /**
     * 定时执行轮询和推进 — 每 30 秒
     * <p>
     * 【执行流程】
     * <ol>
     *   <li>查询所有 PROCESSING 状态且已到轮询时间（nextPollAt &le; now）的任务</li>
     *   <li>遍历任务，加载模型配置后调用厂商接口轮询</li>
     *   <li>查询所有 AI 步骤全部完成（stepStatus=SUCCESS）的剧集</li>
     *   <li>自动调用 advance() 推进到下一状态</li>
     * </ol>
     */
    @Scheduled(fixedDelay = 30_000)
    public void pollAndAdvance() {
        log.debug("[Scheduler] 开始轮询和推进检查");

        // ---- 职责 1：轮询 AI 任务 ----
        pollAiTasks();

        // ---- 职责 2：推进就绪步骤 ----
        advanceReadyEpisodes();

        log.debug("[Scheduler] 轮询和推进检查完成");
    }

    /**
     * 轮询所有处理中的 AI 任务
     * <p>
     * 遍历查询到的 PROCESSING 任务，加载对应的模型配置后
     * 调用 {@link ModelGateway#pollAndUpdate} 查询厂商侧进度。
     * 每个任务独立 try-catch，单个任务异常不影响其他任务轮询。
     */
    private void pollAiTasks() {
        List<Task> tasks = taskService.findProcessingAndReadyToPoll();

        if (tasks.isEmpty()) {
            return;
        }

        log.debug("[Scheduler] 轮询 {} 个 AI 任务", tasks.size());

        for (Task task : tasks) {
            try {
                Optional<ModelConfigEntity> configOpt = configLoader.load(
                        task.getVendor(), task.getModelType());
                if (!configOpt.isPresent()) {
                    log.warn("[Scheduler] 未找到模型配置，跳过轮询: vendor={}, type={}",
                            task.getVendor(), task.getModelType());
                    continue;
                }
                modelGateway.pollAndUpdate(task, configOpt.get());
            } catch (Exception e) {
                log.error("[Scheduler] 轮询任务异常: taskId={}", task.getId(), e);
            }
        }
    }

    /**
     * 推进就绪的剧集
     * <p>
     * 扫描 {@link #AUTO_ADVANCE_STATES} 中定义的状态，查询 stepStatus=SUCCESS
     * 的剧集并自动调用 advance() 推进到下一状态。
     * 每个剧集独立 try-catch，单个推进失败不影响其他剧集。
     */
    private void advanceReadyEpisodes() {
        for (EpisodeStatus state : AUTO_ADVANCE_STATES) {
            List<Episode> readyEpisodes = episodeMapper.selectList(
                    new LambdaQueryWrapper<Episode>()
                            .eq(Episode::getStatus, state.getCode())
                            .eq(Episode::getStepStatus, StepStatus.SUCCESS.getCode())
            );

            if (readyEpisodes.isEmpty()) {
                continue;
            }

            log.info("[Scheduler] 发现 {} 个待推进剧集: state={}", readyEpisodes.size(), state.getLabel());

            for (Episode episode : readyEpisodes) {
                try {
                    log.info("[Scheduler] AI步骤完成，自动推进: episodeId={}, state={}",
                            episode.getId(), state.getLabel());
                    pipelineEngine.advance(episode);
                } catch (Exception e) {
                    log.error("[Scheduler] 自动推进失败: episodeId={}, state={}",
                            episode.getId(), state.getLabel(), e);
                }
            }
        }
    }
}
