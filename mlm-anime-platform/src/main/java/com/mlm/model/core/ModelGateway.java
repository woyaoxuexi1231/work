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
import java.util.Map;

/**
 * 模型调用统一网关 — 所有 AI 请求的入口
 * <p>
 * 【职责】
 * <ol>
 *   <li>接收 GenerateRequest，查找匹配的 ModelAdapter（适配器模式）</li>
 *   <li>提交任务到厂商，创建本地 task 记录（状态 PROCESSING）</li>
 *   <li>TaskPollingScheduler 定时轮询 → pollAndUpdate 更新任务状态</li>
 *   <li>任务完成 → 回写结果（分镜内容/图片 URL 等）到 Episode</li>
 *   <li>某步骤全部完成 → 标记 stepStatus=SUCCESS，TaskPollingScheduler 检测后自动推进</li>
 * </ol>
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
     * <p>
     * 1. 根据 vendor + type 查找适配器
     * 2. 创建本地 task 记录（PROCESSING）
     * 3. 调用 adapter.submit() 提交到厂商
     * 4. 记录 vendorTaskId，后续轮询用
     *
     * @param request 统一生成请求
     * @return 包含本地 taskId 的响应
     */
    @Transactional
    public GenerateResponse generate(GenerateRequest request) {
        ModelAdapter adapter = findAdapter(request.getVendor(), request.getType());

        // 创建本地任务记录
        Task task = new Task();
        task.setEpisodeId(request.getEpisodeId());
        task.setStep(request.getType().getLabel());     // 如 "文生文"、"文生图"
        task.setModelType(request.getType());
        task.setVendor(request.getVendor());
        task.setStatus(StepStatus.PROCESSING);
        task.setRequestJson(toJson(request));
        task.setPollCount(0);
        task.setNextPollAt(LocalDateTime.now().plusSeconds(5));
        taskMapper.insert(task);

        // 提交到厂商
        try {
            String vendorTaskId = adapter.submit(request);
            task.setVendorTaskId(vendorTaskId);
            taskMapper.updateById(task);
            log.info("任务提交成功: taskId={}, episodeId={}, vendor={}, vendorTaskId={}",
                task.getId(), request.getEpisodeId(), request.getVendor(), vendorTaskId);
        } catch (Exception e) {
            task.setStatus(StepStatus.FAILED);
            taskMapper.updateById(task);
            log.error("任务提交失败: taskId={}, episodeId={}, vendor={}", task.getId(), request.getEpisodeId(), request.getVendor(), e);
        }

        return GenerateResponse.processing(task.getId());
    }

    /**
     * 轮询单个任务状态（由 TaskPollingScheduler 定时调用）
     * <p>
     * 逻辑：
     * <ul>
     *   <li>超过最大轮询次数 → FAILED</li>
     *   <li>厂商返回 SUCCESS → 格式校验 → 通过则保存结果并检查步骤完成</li>
     *   <li>格式校验失败且未超重试次数 → 自动重提</li>
     *   <li>厂商返回 PROCESSING → 计算下次轮询时间</li>
     * </ul>
     */
    @Transactional
    public void pollAndUpdate(Task task, ModelConfigEntity config) {
        // 超限检查
        if (task.getPollCount() >= task.getMaxPollCount()) {
            task.setStatus(StepStatus.FAILED);
            taskMapper.updateById(task);
            log.warn("任务超过最大轮询次数: taskId={}, pollCount={}, max={}",
                task.getId(), task.getPollCount(), task.getMaxPollCount());
            return;
        }

        ModelAdapter adapter = findAdapter(task.getVendor(), task.getModelType());

        try {
            // 查询厂商状态
            ModelAdapter.TaskStatus vendorStatus = adapter.queryStatus(task.getVendorTaskId());
            task.setPollCount(task.getPollCount() + 1);

            switch (vendorStatus) {
                case SUCCESS -> handleSuccess(task, adapter, vendorStatus, config);
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

    /**
     * 处理厂商返回 SUCCESS：
     * 1. 解析结果
     * 2. 格式校验（失败则自动重试）
     * 3. 校验通过 → 保存结果到 task + 回写到 episode
     * 4. 检查当前步骤是否全部完成
     */
    private void handleSuccess(Task task, ModelAdapter adapter, ModelAdapter.TaskStatus vendorStatus, ModelConfigEntity config) {
        GenerateResponse result = adapter.parseResult(task.getVendorTaskId(), vendorStatus.getRawData());

        // 格式校验
        if (!formatValidator.validate(result, task.getModelType())) {
            if (task.getPollCount() < config.getMaxRetries()) {
                log.warn("格式校验失败，自动重试: taskId={}, retryCount={}", task.getId(), task.getPollCount());
                task.setPollCount(0);
                String newVendorTaskId = adapter.submit(rebuildRequest(task));
                task.setVendorTaskId(newVendorTaskId);
                return;
            }
            task.setStatus(StepStatus.FAILED);
            return;
        }

        // 校验通过 → 保存结果
        task.setStatus(StepStatus.SUCCESS);
        task.setResultJson(toJson(result));
        log.info("任务完成: taskId={}, vendorTaskId={}", task.getId(), task.getVendorTaskId());

        // 回写 AI 产出到 Episode（如分镜内容、图片 URL 等）
        saveResultToEpisode(task, result);

        // 检查步骤是否全部完成
        checkStepCompletion(task);
    }

    /**
     * 将 AI 结果回写到 Episode 实体
     * <p>
     * 不同模型类型写不同字段：
     * <ul>
     *   <li>分镜生成（由 StoryboardStepHandler 提交）→ 写入 storyboard_content</li>
     *   <li>文生图/图生视频 → 结果通过 result_resource_id 关联</li>
     * </ul>
     */
    private void saveResultToEpisode(Task task, GenerateResponse result) {
        Episode episode = episodeMapper.selectById(task.getEpisodeId());
        if (episode == null) return;

        // 如果任务来源于 STORYBOARD 步骤，分镜内容回写到 episode.storyboardContent
        if (task.getModelType() == ModelType.TEXT_TO_TEXT) {
            String rawText = null;
            if (result.getExtensions() != null) {
                Object raw = result.getExtensions().get("rawText");
                if (raw != null) rawText = raw.toString();
            }
            if (rawText != null) {
                episode.setStoryboardContent(rawText);
                episodeMapper.updateById(episode);
                log.info("分镜内容已回写: episodeId={}, length={}", episode.getId(), rawText.length());
            }
        }
    }

    /**
     * 检查某剧集某步骤的全部任务是否都已完成
     * <p>
     * 如果全部完成 → 设置 stepStatus = SUCCESS
     * TaskPollingScheduler 在下一轮调度中会自动推进剧集到下一状态。
     */
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

    /** 根据 vendor + type 查找匹配的适配器 */
    private ModelAdapter findAdapter(String vendor, ModelType type) {
        return adapters.stream()
            .filter(a -> a.vendor().equals(vendor) && a.supports(type))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("无匹配的模型适配器: vendor=" + vendor + ", type=" + type));
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) {
            log.warn("JSON 序列化异常: {}", e.getMessage());
            return "{}";
        }
    }

    /** 从 task.requestJson 重建 GenerateRequest（用于格式校验失败后重试） */
    private GenerateRequest rebuildRequest(Task task) {
        try { return objectMapper.readValue(task.getRequestJson(), GenerateRequest.class); }
        catch (Exception e) { throw new RuntimeException("重建请求失败", e); }
    }
}
