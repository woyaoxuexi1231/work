package com.mlm.pipeline.handler;

import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.ModelType;
import com.mlm.model.core.GenerateRequest;
import com.mlm.model.core.ModelGateway;
import com.mlm.pipeline.engine.StepHandler;
import com.mlm.pipeline.entity.Episode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 【策略实现】拆分镜步骤处理器 — 对应 {@link EpisodeStatus#STORYBOARD}(4)
 * <p>
 * 【职责】
 * 剧本审核通过后自动触发，调用 AI 文生文模型将剧本拆分为分镜脚本。
 * 每个分镜包含：镜头号、画面描述、持续时间、运镜方式、角色位置。
 * <p>
 * 【数据流】
 * AI 返回的分镜 JSON 通过
 * {@link com.mlm.model.core.ModelGateway#generate(GenerateRequest)} 的异步回调
 * 自动回写到 {@link Episode#getStoryboardContent()} 字段，
 * 前端生成工作台从此字段读取分镜列表。
 * <p>
 * 【重要说明】
 * 本步骤是 Pipeline 中<em>唯一一个全自动处理</em>的步骤 —
 * 从剧本审核通过到分镜生成完成，不需要人工干预。
 *
 * @author mlm
 * @see ReviewStepHandler 上一环节（剧本审核）
 * @see GeneratingStepHandler 下一环节（AI成片）
 * @see ModelGateway AI 模型调用网关
 */
@Component
public class StoryboardStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(StoryboardStepHandler.class);

    private static final String AI_VENDOR = "openai";
    private static final String SYSTEM_PROMPT_TEMPLATE =
            "你是一个专业的分镜师，请将以下剧本拆分为分镜脚本：\n"
                    + "剧本：%s\n"
                    + "要求：每个分镜包含镜头号、画面描述、持续时间、运镜方式、角色位置。\n"
                    + "输出格式为JSON数组。";

    private final ModelGateway modelGateway;

    /**
     * 构造拆分镜处理器
     *
     * @param modelGateway AI 模型调用网关
     */
    public StoryboardStepHandler(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 返回 {@link EpisodeStatus#STORYBOARD}。
     */
    @Override
    public EpisodeStatus step() {
        return EpisodeStatus.STORYBOARD;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【实现说明】
     * 将审核通过的剧本内容提交给 AI 进行分镜拆分。
     * AI 调用是异步的，拆分结果通过轮询机制写入数据库。
     * 所有拆分任务完成后，调度器自动推进到 GENERATING 状态。
     */
    @Override
    public void handle(Episode episode) {
        log.info("[StepHandler] STORYBOARD 开始 — episodeId={}, projectId={}, episodeNumber={}",
                episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());

        GenerateRequest request = new GenerateRequest();
        request.setType(ModelType.TEXT_TO_TEXT);
        request.setVendor(AI_VENDOR);
        request.setPrompt(buildStoryboardPrompt(episode));
        request.setEpisodeId(episode.getId());

        modelGateway.generate(request);

        log.info("[StepHandler] STORYBOARD 提交完成 — episodeId={}", episode.getId());
    }

    /**
     * 构建 AI 分镜拆分提示词
     * <p>
     * 包含审核通过的剧本内容，要求 AI 按分镜格式输出 JSON 数组。
     *
     * @param episode 当前剧集
     * @return 完整的 AI 提示词
     */
    private String buildStoryboardPrompt(Episode episode) {
        String scriptContent = (episode.getScriptContent() != null)
                ? episode.getScriptContent()
                : "无";
        return String.format(SYSTEM_PROMPT_TEMPLATE, scriptContent);
    }
}
