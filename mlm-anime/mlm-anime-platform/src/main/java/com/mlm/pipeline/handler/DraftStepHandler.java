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
 * 【策略实现】剧本创作步骤处理器 — 对应 {@link EpisodeStatus#SCRIPT_DRAFT}(2)
 * <p>
 * 【职责】
 * 将用户手写的剧本内容（{@link Episode#getScriptContent()}）提交给 AI 做润色和格式化。
 * <p>
 * 【处理流程】
 * <ol>
 *   <li>读取用户提交的剧本原始内容</li>
 *   <li>构建 AI 润色提示词（包含剧集标题和原始内容）</li>
 *   <li>调用 OpenAI 文生文模型提交润色任务</li>
 *   <li>AI 异步完成后自动推进到 {@link EpisodeStatus#SCRIPT_REVIEW} 等待人工审核</li>
 * </ol>
 * <p>
 * 【设计决策】
 * 为什么不用 AI 直接生成剧本？
 * <blockquote>
 * 创作方向应由用户掌控，AI 只做润色和格式优化。用户先写初稿，
 * AI 将其格式化为分场结构，然后提交人工审核。
 * </blockquote>
 *
 * @author mlm
 * @see ReviewStepHandler 下一环节（剧本审核）
 * @see ModelGateway AI 模型调用网关
 */
@Component
public class DraftStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(DraftStepHandler.class);

    private static final String AI_VENDOR = "openai";
    private static final String SYSTEM_PROMPT_TEMPLATE =
            "你是一个专业的动漫编剧，请根据以下内容润色一集动漫剧本：\n"
                    + "剧集标题：%s\n"
                    + "原始内容：%s\n"
                    + "要求：包含角色对话、场景描述、情绪指引，输出格式为分场结构。";

    private final ModelGateway modelGateway;

    /**
     * 构造剧本创作处理器
     *
     * @param modelGateway AI 模型调用网关
     */
    public DraftStepHandler(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 返回 {@link EpisodeStatus#SCRIPT_DRAFT}。
     */
    @Override
    public EpisodeStatus step() {
        return EpisodeStatus.SCRIPT_DRAFT;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【实现说明】
     * 将用户剧本内容构建为提示词，调用 OpenAI 文生文模型进行润色。
     * 润色后的剧本通过异步回调写入数据库，然后引擎自动推进到审核状态。
     */
    @Override
    public void handle(Episode episode) {
        log.info("[StepHandler] SCRIPT_DRAFT 开始 — episodeId={}, projectId={}, episodeNumber={}",
                episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());

        GenerateRequest request = new GenerateRequest();
        request.setType(ModelType.TEXT_TO_TEXT);
        request.setVendor(AI_VENDOR);
        request.setPrompt(buildScriptPrompt(episode));
        request.setEpisodeId(episode.getId());

        modelGateway.generate(request);

        log.info("[StepHandler] SCRIPT_DRAFT 提交完成 — episodeId={}", episode.getId());
    }

    /**
     * 构建 AI 剧本润色提示词
     * <p>
     * 包含剧集标题、用户原始内容和格式要求。
     *
     * @param episode 当前剧集
     * @return 完整的 AI 提示词
     */
    private String buildScriptPrompt(Episode episode) {
        String scriptContent = (episode.getScriptContent() != null)
                ? episode.getScriptContent()
                : "无";
        return String.format(SYSTEM_PROMPT_TEMPLATE,
                episode.getTitle(),
                scriptContent);
    }
}
