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
import com.mlm.pipeline.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 模型网关 — 统一管理 AI 生成任务的提交、轮询和结果处理
 * <p>
 * 【职责】
 * <ul>
 *   <li>任务提交：选择适配器 → 创建任务记录 → 调用厂商 API 提交</li>
 *   <li>任务轮询：查询厂商侧状态 → 处理结果 → 验证格式 → 自动重试</li>
 *   <li>步骤完成检查：当步骤的所有任务成功时更新 stepStatus</li>
 * </ul>
 * <p>
 * 【设计模式】外观模式（Facade）
 * 对外提供统一的 {@link #generate(GenerateRequest)} 和
 * {@link #pollAndUpdate(Task, ModelConfigEntity)} 接口，
 * 屏蔽了厂商适配器选择、任务持久化、结果验证等内部细节。
 * <p>
 * 【线程安全】
 * 使用线程安全的 {@link ObjectMapper}（JDK  since 6 开始保证线程安全），
 * 无可变成员状态，可在多线程环境下安全使用。
 *
 * @author mlm
 * @see ModelAdapter
 * @see FormatValidator
 */
@Component
public class ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(ModelGateway.class);

    /** 线程安全的 JSON 处理器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 子状态常量（int 码，与 StepStatus 枚举对齐） */
    static final int ST_PENDING = StepStatus.PENDING.getCode();
    static final int ST_PROCESSING = StepStatus.PROCESSING.getCode();
    static final int ST_SUCCESS = StepStatus.SUCCESS.getCode();
    static final int ST_FAILED = StepStatus.FAILED.getCode();

    private final List<ModelAdapter> adapters;
    private final TaskService taskService;
    private final EpisodeMapper episodeMapper;
    private final FormatValidator formatValidator;
    private final ModelConfigLoader configLoader;

    /**
     * 构造模型网关
     *
     * @param adapters      所有已注册的模型适配器（Spring 自动注入）
     * @param taskService   任务服务（任务 CRUD）
     * @param episodeMapper 剧集 Mapper（步骤完成检查）
     * @param formatValidator AI 返回格式校验器
     * @param configLoader  模型配置加载器
     */
    public ModelGateway(List<ModelAdapter> adapters,
                        TaskService taskService,
                        EpisodeMapper episodeMapper,
                        FormatValidator formatValidator,
                        ModelConfigLoader configLoader) {
        this.adapters = adapters;
        this.taskService = taskService;
        this.episodeMapper = episodeMapper;
        this.formatValidator = formatValidator;
        this.configLoader = configLoader;
    }

    // ======================== 公开方法 ========================

    /**
     * 提交 AI 生成任务
     * <p>
     * 【执行流程】
     * <ol>
     *   <li>根据 vendor + type 匹配对应的 {@link ModelAdapter}</li>
     *   <li>创建任务记录（状态 = PROCESSING）</li>
     *   <li>调用厂商 API 提交任务，获取 vendorTaskId</li>
     *   <li>返回处理中的响应（含本地 taskId）</li>
     * </ol>
     *
     * @param request 统一生成请求
     * @return 处理中的响应（含本地任务 ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public GenerateResponse generate(GenerateRequest request) {
        ModelAdapter adapter = findAdapter(request.getVendor(), request.getType());

        Task task = createTask(request);
        log.info("[ModelGateway] 提交AI任务: taskId={}, vendor={}, type={}, episodeId={}",
                task.getId(), request.getVendor(), request.getType(), request.getEpisodeId());

        try {
            String vendorTaskId = adapter.submit(request);
            task.setVendorTaskId(vendorTaskId);
            taskService.save(task);
            log.info("[ModelGateway] 厂商提交成功: taskId={}, vendorTaskId={}",
                    task.getId(), vendorTaskId);
        } catch (Exception e) {
            task.setStatus(ST_FAILED);
            taskService.save(task);
            log.error("[ModelGateway] 厂商提交失败: taskId={}", task.getId(), e);
        }

        return GenerateResponse.processing(task.getId());
    }

    /**
     * 轮询并更新任务状态
     * <p>
     * 查询厂商侧的 AI 任务进度，根据返回状态更新本地任务记录：
     * <ul>
     *   <li>PROCESSING → 计算下次轮询时间并保存</li>
     *   <li>SUCCESS → 验证格式，通过则标记成功并检查步骤完成</li>
     *   <li>FAILED → 标记失败</li>
     * </ul>
     *
     * @param task   待轮询的任务
     * @param config 模型配置（含轮询间隔、重试次数等）
     */
    @Transactional(rollbackFor = Exception.class)
    public void pollAndUpdate(Task task, ModelConfigEntity config) {
        // 检查轮询次数上限
        if (task.getPollCount() >= task.getMaxPollCount()) {
            log.warn("[ModelGateway] 轮询超限，标记失败: taskId={}, pollCount={}",
                    task.getId(), task.getPollCount());
            task.setStatus(ST_FAILED);
            taskService.save(task);
            return;
        }

        ModelAdapter adapter = findAdapter(task.getVendor(), task.getModelType());

        try {
            ModelAdapter.TaskStatus vendorStatus = adapter.queryStatus(task.getVendorTaskId());
            task.setPollCount(task.getPollCount() + 1);

            switch (vendorStatus) {
                case SUCCESS:
                    handleTaskSuccess(task, adapter, vendorStatus, config);
                    break;
                case FAILED:
                    log.warn("[ModelGateway] 厂商返回失败: taskId={}", task.getId());
                    task.setStatus(ST_FAILED);
                    break;
                case PROCESSING:
                    task.scheduleNextPoll(config.getPollInterval());
                    break;
            }
            taskService.save(task);
        } catch (Exception e) {
            log.error("[ModelGateway] 轮询异常: taskId={}", task.getId(), e);
            task.setPollCount(task.getPollCount() + 1);
            task.scheduleNextPoll(config.getPollInterval());
            taskService.save(task);
        }
    }

    // ======================== 内部方法 ========================

    /**
     * 处理任务成功返回
     * <p>
     * 解析厂商返回结果 → 格式校验 → 通过则标记成功并保存结果，
     * 不通过则自动重试（若未超过重试次数上限）。
     *
     * @param task          当前任务
     * @param adapter       模型适配器
     * @param vendorStatus  厂商返回状态
     * @param config        模型配置
     */
    private void handleTaskSuccess(Task task, ModelAdapter adapter,
                                   ModelAdapter.TaskStatus vendorStatus,
                                   ModelConfigEntity config) {
        GenerateResponse result = adapter.parseResult(task.getVendorTaskId(), vendorStatus.getRawData());

        if (!formatValidator.validate(result, task.getModelType())) {
            if (task.getPollCount() < config.getMaxRetries()) {
                log.warn("[ModelGateway] 格式校验失败，自动重试: taskId={}, retry={}/{}",
                        task.getId(), task.getPollCount(), config.getMaxRetries());
                task.setPollCount(0);
                task.setVendorTaskId(adapter.submit(rebuildRequest(task)));
                return;
            }
            log.warn("[ModelGateway] 格式校验失败且超出重试上限: taskId={}", task.getId());
            task.setStatus(ST_FAILED);
            return;
        }

        task.setStatus(ST_SUCCESS);
        task.setResultJson(toJson(result));
        log.info("[ModelGateway] 任务完成: taskId={}, vendor={}", task.getId(), task.getVendor());

        // 回写分镜内容到剧集（仅文生文类型）
        saveResultToEpisode(task, result);

        // 检查当前步骤是否全部完成
        checkStepCompletion(task);
    }

    /**
     * 将 AI 生成结果回写到剧集实体
     * <p>
     * 仅 TEXT_TO_TEXT（剧本润色/分镜拆分）的结果需要回写，
     * 图片和视频类结果由 OSS URL 引用。
     *
     * @param task   已完成的任务
     * @param result 生成结果
     */
    private void saveResultToEpisode(Task task, GenerateResponse result) {
        if (task.getModelType() != ModelType.TEXT_TO_TEXT) {
            return;
        }

        Episode episode = episodeMapper.selectById(task.getEpisodeId());
        if (episode == null) {
            log.warn("[ModelGateway] 剧集不存在，无法回写结果: episodeId={}", task.getEpisodeId());
            return;
        }

        String rawText = (result.getExtensions() != null)
                ? (String) result.getExtensions().get("rawText")
                : null;

        if (rawText != null) {
            episode.setStoryboardContent(rawText);
            episodeMapper.updateById(episode);
            log.info("[ModelGateway] 分镜内容回写成功: episodeId={}", episode.getId());
        }
    }

    /**
     * 检查当前剧集步骤的所有任务是否全部完成
     * <p>
     * 当某步骤的所有 AI 任务都成功完成时，将剧集的 stepStatus 置为 SUCCESS，
     * {@link com.mlm.pipeline.scheduler.TaskPollingScheduler} 会扫描到
     * 这个变化并自动调用 {@link com.mlm.pipeline.engine.PipelineEngine#advance} 推进到下一状态。
     */
    private void checkStepCompletion(Task completedTask) {
        List<Task> tasks = taskService.findByEpisodeAndStep(
                completedTask.getEpisodeId(), completedTask.getStep());

        boolean allDone = tasks.stream().allMatch(t -> t.getStatus() == ST_SUCCESS);

        if (allDone) {
            Episode episode = episodeMapper.selectById(completedTask.getEpisodeId());
            if (episode != null) {
                episode.setStepStatus(ST_SUCCESS);
                episodeMapper.updateById(episode);
                log.info("[ModelGateway] 步骤全部完成: episodeId={}, step={}",
                        episode.getId(), completedTask.getStep());
            }
        }
    }

    /**
     * 创建任务实体并持久化
     *
     * @param request 生成请求
     * @return 已持久化的任务（含自增 ID）
     */
    private Task createTask(GenerateRequest request) {
        Task task = new Task();
        task.setEpisodeId(request.getEpisodeId());
        task.setStep(request.getType().getLabel());
        task.setModelType(request.getType());
        task.setVendor(request.getVendor());
        task.setStatus(ST_PROCESSING);
        task.setRequestJson(toJson(request));
        task.setPollCount(0);
        task.setNextPollAt(LocalDateTime.now().plusSeconds(5));
        taskService.create(task);
        return task;
    }

    /**
     * 查找匹配的模型适配器
     *
     * @param vendor 厂商标识
     * @param type   模型类型
     * @return 匹配的适配器
     * @throws IllegalArgumentException 无匹配适配器时抛出
     */
    private ModelAdapter findAdapter(String vendor, ModelType type) {
        return adapters.stream()
                .filter(a -> a.vendor().equals(vendor) && a.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "无匹配适配器: vendor=" + vendor + ", type=" + type));
    }

    /**
     * 将对象序列化为 JSON 字符串
     *
     * @param obj 待序列化对象
     * @return JSON 字符串，失败时返回 "{}"
     */
    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[ModelGateway] JSON 序列化失败", e);
            return "{}";
        }
    }

    /**
     * 从任务的 requestJson 重建 GenerateRequest
     *
     * @param task 当前任务
     * @return 重建的生成请求
     * @throws RuntimeException JSON 解析失败时抛出
     */
    private GenerateRequest rebuildRequest(Task task) {
        try {
            return OBJECT_MAPPER.readValue(task.getRequestJson(), GenerateRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("重建请求失败: taskId=" + task.getId(), e);
        }
    }
}
