package com.mlm.model.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public ModelGateway(List<ModelAdapter> adapters, TaskMapper taskMapper,
                        TaskService taskService, EpisodeMapper episodeMapper,
                        FormatValidator formatValidator, ModelConfigLoader configLoader) {
        this.adapters = adapters;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.episodeMapper = episodeMapper;
        this.formatValidator = formatValidator;
        this.configLoader = configLoader;
    }

    /** step → enum 方便标签获取 */
    static final int ST_PENDING = StepStatus.PENDING.getCode();     // 0
    static final int ST_PROCESSING = StepStatus.PROCESSING.getCode();// 2
    static final int ST_SUCCESS = StepStatus.SUCCESS.getCode();     // 1
    static final int ST_FAILED = StepStatus.FAILED.getCode();       // -1

    @Transactional
    public GenerateResponse generate(GenerateRequest request) {
        ModelAdapter adapter = findAdapter(request.getVendor(), request.getType());
        Task task = new Task();
        task.setEpisodeId(request.getEpisodeId());
        task.setStep(request.getType().getLabel());
        task.setModelType(request.getType());
        task.setVendor(request.getVendor());
        task.setStatus(ST_PROCESSING);
        task.setRequestJson(toJson(request));
        task.setPollCount(0);
        task.setNextPollAt(LocalDateTime.now().plusSeconds(5));
        taskMapper.insert(task);
        try {
            String vendorTaskId = adapter.submit(request);
            task.setVendorTaskId(vendorTaskId);
            taskMapper.updateById(task);
        } catch (Exception e) {
            task.setStatus(ST_FAILED);
            taskMapper.updateById(task);
            log.error("任务提交失败: taskId={}", task.getId(), e);
        }
        return GenerateResponse.processing(task.getId());
    }

    @Transactional
    public void pollAndUpdate(Task task, ModelConfigEntity config) {
        if (task.getPollCount() >= task.getMaxPollCount()) {
            task.setStatus(ST_FAILED);
            taskMapper.updateById(task);
            return;
        }
        ModelAdapter adapter = findAdapter(task.getVendor(), task.getModelType());
        try {
            ModelAdapter.TaskStatus vendorStatus = adapter.queryStatus(task.getVendorTaskId());
            task.setPollCount(task.getPollCount() + 1);
            switch (vendorStatus) {
                case SUCCESS -> handleSuccess(task, adapter, vendorStatus, config);
                case FAILED -> { task.setStatus(ST_FAILED); }
                case PROCESSING -> { task.scheduleNextPoll(config.getPollInterval()); }
            }
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.error("轮询异常: taskId={}", task.getId(), e);
            task.setPollCount(task.getPollCount() + 1);
            task.scheduleNextPoll(config.getPollInterval());
            taskMapper.updateById(task);
        }
    }

    private void handleSuccess(Task task, ModelAdapter adapter,
                                ModelAdapter.TaskStatus vendorStatus, ModelConfigEntity config) {
        GenerateResponse result = adapter.parseResult(task.getVendorTaskId(), vendorStatus.getRawData());
        if (!formatValidator.validate(result, task.getModelType())) {
            if (task.getPollCount() < config.getMaxRetries()) {
                task.setPollCount(0);
                task.setVendorTaskId(adapter.submit(rebuildRequest(task)));
                return;
            }
            task.setStatus(ST_FAILED);
            return;
        }
        task.setStatus(ST_SUCCESS);
        task.setResultJson(toJson(result));
        saveResultToEpisode(task, result);
        checkStepCompletion(task);
    }

    private void saveResultToEpisode(Task task, GenerateResponse result) {
        Episode episode = episodeMapper.selectById(task.getEpisodeId());
        if (episode == null || task.getModelType() != ModelType.TEXT_TO_TEXT) return;
        String rawText = result.getExtensions() == null ? null
            : (String) result.getExtensions().get("rawText");
        if (rawText != null) {
            episode.setStoryboardContent(rawText);
            episodeMapper.updateById(episode);
            log.info("分镜回写: episodeId={}", episode.getId());
        }
    }

    private void checkStepCompletion(Task completedTask) {
        List<Task> tasks = taskService.findByEpisodeAndStep(
            completedTask.getEpisodeId(), completedTask.getStep());
        boolean allDone = tasks.stream().allMatch(t -> t.getStatus() == ST_SUCCESS);
        if (allDone) {
            Episode episode = episodeMapper.selectById(completedTask.getEpisodeId());
            if (episode != null) {
                episode.setStepStatus(ST_SUCCESS);
                episodeMapper.updateById(episode);
                log.info("步骤全部完成: episodeId={}, step={}", episode.getId(), completedTask.getStep());
            }
        }
    }

    private ModelAdapter findAdapter(String vendor, ModelType type) {
        return adapters.stream()
            .filter(a -> a.vendor().equals(vendor) && a.supports(type))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("无匹配适配器: vendor=" + vendor + ", type=" + type));
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private GenerateRequest rebuildRequest(Task task) {
        try { return objectMapper.readValue(task.getRequestJson(), GenerateRequest.class); }
        catch (Exception e) { throw new RuntimeException("重建请求失败", e); }
    }
}
