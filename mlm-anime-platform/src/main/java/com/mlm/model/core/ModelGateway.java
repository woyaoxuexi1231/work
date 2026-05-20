package com.mlm.model.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.ModelType;
import com.mlm.common.enums.StepStatus;
import com.mlm.model.config.ModelConfigEntity;
import com.mlm.model.config.ModelConfigLoader;
import com.mlm.model.retry.FormatValidator;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Task;
import com.mlm.pipeline.mapper.EpisodeMapper;
import com.mlm.pipeline.mapper.TaskMapper;
import com.mlm.pipeline.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型调用统一网关
 * <p>
 * 所有 AI 生成请求的入口。提交任务到厂商后创建本地 task 记录，
 * 由 TaskPollingScheduler 定时轮询厂商结果。
 * 任务完成后检查该剧集该步骤是否全部完成，是则推进 Pipeline。
 */
@Component
public class ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(ModelGateway.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final List<ModelAdapter> adapters;
    private final TaskMapper taskMapper;
    private final TaskService taskService;
    private final EpisodeMapper episodeMapper;
    private final FormatValidator formatValidator;
    private final ModelConfigLoader configLoader;

    public ModelGateway(List<ModelAdapter> adapters,
                        TaskMapper taskMapper,
                        TaskService taskService,
                        EpisodeMapper episodeMapper,
                        FormatValidator formatValidator,
                        ModelConfigLoader configLoader) {
        this.adapters = adapters;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.episodeMapper = episodeMapper;
        this.formatValidator = formatValidator;
        this.configLoader = configLoader;
    }

    /**
     * 提交 AI 生成任务
     *
     * @param request 统一生成请求
     * @return 处理中响应（含 taskId）
     */
    @Transactional
    public GenerateResponse generate(GenerateRequest request) {
        ModelAdapter adapter = findAdapter(request.getVendor(), request.getType());

        Task task = new Task();
        task.setEpisodeId(request.getEpisodeId());
        task.setStep(request.getType().getLabel());
        task.setModelType(request.getType());
        task.setVendor(request.getVendor());
        task.setStatus(StepStatus.PROCESSING);
        task.setRequestJson(toJson(request));
        task.setPollCount(0);
        task.setNextPollAt(LocalDateTime.now().plusSeconds(5));
        taskMapper.insert(task);

        try {
            String vendorTaskId = adapter.submit(request);
            task.setVendorTaskId(vendorTaskId);
            taskMapper.updateById(task);
            log.info("任务提交成功: taskId={}, episodeId={}, vendor={}, vendorTaskId={}",
                task.getId(), request.getEpisodeId(), request.getVendor(), vendorTaskId);
        } catch (Exception e) {
            task.setStatus(StepStatus.FAILED);
            taskMapper.updateById(task);
            log.error("任务提交失败: taskId={}, episodeId={}, vendor={}",
                task.getId(), request.getEpisodeId(), request.getVendor(), e);
        }

        return GenerateResponse.processing(task.getId());
    }

    /**
     * 轮询并更新单个任务状态
     */
    @Transactional
    public void pollAndUpdate(Task task, ModelConfigEntity config) {
        if (task.getPollCount() >= task.getMaxPollCount()) {
            task.setStatus(StepStatus.FAILED);
            taskMapper.updateById(task);
            log.warn("任务超过最大轮询次数: taskId={}, pollCount={}", task.getId(), task.getPollCount());
            return;
        }

        ModelAdapter adapter = findAdapter(task.getVendor(), task.getModelType());

        try {
            ModelAdapter.TaskStatus vendorStatus = adapter.queryStatus(task.getVendorTaskId());
            task.setPollCount(task.getPollCount() + 1);

            switch (vendorStatus) {
                case SUCCESS -> {
                    GenerateResponse result = adapter.parseResult(task.getVendorTaskId(), vendorStatus.getRawData());
                    if (!formatValidator.validate(result, task.getModelType())) {
                        if (task.getPollCount() < config.getMaxRetries()) {
                            log.warn("格式校验失败，自动重试: taskId={}, retryCount={}", task.getId(), task.getPollCount());
                            task.setPollCount(0);
                            String newVendorTaskId = adapter.submit(rebuildRequest(task));
                            task.setVendorTaskId(newVendorTaskId);
                            taskMapper.updateById(task);
                            return;
                        }
                        task.setStatus(StepStatus.FAILED);
                    } else {
                        task.setStatus(StepStatus.SUCCESS);
                        task.setResultJson(toJson(result));
                        checkStepCompletion(task);
                    }
                }
                case FAILED -> {
                    log.warn("厂商返回失败: taskId={}, vendorTaskId={}", task.getId(), task.getVendorTaskId());
                    task.setStatus(StepStatus.FAILED);
                }
                case PROCESSING -> {
                    task.scheduleNextPoll(config.getPollInterval());
                    log.debug("任务处理中: taskId={}, pollCount={}", task.getId(), task.getPollCount());
                }
            }

            taskMapper.updateById(task);
        } catch (Exception e) {
            log.error("轮询任务异常: taskId={}, vendorTaskId={}", task.getId(), task.getVendorTaskId(), e);
            task.setPollCount(task.getPollCount() + 1);
            task.scheduleNextPoll(config.getPollInterval());
            taskMapper.updateById(task);
        }
    }

    /** 检查某剧集的某步骤是否全部任务完成，是则推进剧集步骤 */
    private void checkStepCompletion(Task completedTask) {
        List<Task> stepTasks = taskService.findByEpisodeAndStep(completedTask.getEpisodeId(), completedTask.getStep());
        boolean allDone = stepTasks.stream().allMatch(t -> t.getStatus() == StepStatus.SUCCESS);
        if (allDone) {
            Episode episode = episodeMapper.selectById(completedTask.getEpisodeId());
            if (episode != null) {
                episode.setStepStatus(StepStatus.SUCCESS);
                episodeMapper.updateById(episode);
                log.info("剧集步骤全部完成: episodeId={}, step={}, 共{}个任务",
                    episode.getId(), completedTask.getStep(), stepTasks.size());
            }
        }
    }

    private ModelAdapter findAdapter(String vendor, ModelType type) {
        return adapters.stream()
            .filter(a -> a.vendor().equals(vendor) && a.supports(type))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("无匹配的模型适配器: vendor=" + vendor + ", type=" + type));
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) {
            log.warn("JSON序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    private GenerateRequest rebuildRequest(Task task) {
        try { return objectMapper.readValue(task.getRequestJson(), GenerateRequest.class); }
        catch (Exception e) { throw new RuntimeException("重建请求失败", e); }
    }
}
