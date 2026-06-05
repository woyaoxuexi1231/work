package com.mlm.pipeline.engine;

import com.mlm.common.enums.EpisodeStatus;
import com.mlm.pipeline.entity.Episode;

/**
 * 步骤处理器接口 — Pipeline 中各步骤的业务逻辑抽象
 * <p>
 * 策略模式（Strategy Pattern）：每个 {@link EpisodeStatus} 对应一个
 * Handler 实现。新增步骤只需实现此接口并注册为 Spring Bean，
 * {@link StepHandlerRegistry} 会自动收集。
 * <p>
 * 实现类列表：
 * <ul>
 *   <li>{@link com.mlm.pipeline.handler.DraftStepHandler} — 剧本创作 (SCRIPT_DRAFT)</li>
 *   <li>{@link com.mlm.pipeline.handler.ReviewStepHandler} — 剧本审核 (SCRIPT_REVIEW)</li>
 *   <li>{@link com.mlm.pipeline.handler.StoryboardStepHandler} — 拆分镜 (STORYBOARD)</li>
 *   <li>{@link com.mlm.pipeline.handler.GeneratingStepHandler} — AI成片 (GENERATING)</li>
 *   <li>{@link com.mlm.pipeline.handler.ApprovalStepHandler} — 终审 (EPISODE_APPROVAL)</li>
 * </ul>
 * <p>
 * 调用链路：PipelineEngine.advance() → StepHandlerRegistry.get() → StepHandler.handle()
 *
 * @author mlm
 * @see StepHandlerRegistry
 * @see com.mlm.pipeline.engine.PipelineEngine
 */
public interface StepHandler {

    /**
     * 返回该 Handler 对应的剧集状态
     * <p>
     * {@link StepHandlerRegistry} 根据此返回值建立 EpisodeStatus → Handler 映射。
     * 每个 Handler 负责且仅负责一个状态的处理。
     *
     * @return 对应的剧集状态枚举
     */
    EpisodeStatus step();

    /**
     * 执行当前步骤的业务逻辑
     * <p>
     * 【约定】
     * <ul>
     *   <li>方法内不负责状态更新 — 状态已由 PipelineEngine 提前推进</li>
     *   <li>抛出异常表示步骤失败 — PipelineEngine 会捕获并标记 stepStatus=FAILED</li>
     *   <li>方法正常返回表示提交成功 — 不代表 AI 异步任务完成</li>
     * </ul>
     *
     * @param episode 当前剧集实体（含已推进的最新状态）
     */
    void handle(Episode episode);
}
