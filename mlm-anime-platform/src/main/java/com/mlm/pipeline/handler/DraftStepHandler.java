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
 * 【策略实现】剧本创作步骤处理器 — 对应 EpisodeStatus.SCRIPT_DRAFT(2)
 * <p>
 * 将用户手写的剧本内容（episode.scriptContent）提交给 AI 做润色和格式化。
 * 当前实现调用 OpenAI（文生文模型），将内容发送到模型网关后即返回。
 * AI 异步完成后自动推进到 SCRIPT_REVIEW 等待人工审核。
 * <p>
 * 为什么不用 AI 直接生成剧本？—— 创作方向应由用户掌控，AI 只做润色。
 */
@Component
public class DraftStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(DraftStepHandler.class);
    private final ModelGateway modelGateway;

    public DraftStepHandler(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    @Override
    public EpisodeStatus step() { return EpisodeStatus.SCRIPT_DRAFT; }

    @Override
    public void handle(Episode episode) {
        log.info(">>>>> SCRIPT_DRAFT 开始: episodeId={}, projectId={}, 集号={}",
            episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());

        // 将剧本内容提交给 AI 润色
        GenerateRequest request = new GenerateRequest();
        request.setType(ModelType.TEXT_TO_TEXT);
        request.setVendor("openai");
        request.setPrompt(buildScriptPrompt(episode));
        request.setEpisodeId(episode.getId());

        modelGateway.generate(request);
        log.info("<<<<< SCRIPT_DRAFT 提交完成: episodeId={}", episode.getId());
    }

    private String buildScriptPrompt(Episode episode) {
        return "你是一个专业的动漫编剧，请根据以下内容润色一集动漫剧本：\n" +
               "剧集标题：" + episode.getTitle() + "\n" +
               "原始内容：" + (episode.getScriptContent() != null ? episode.getScriptContent() : "无") + "\n" +
               "要求：包含角色对话、场景描述、情绪指引，输出格式为分场结构。";
    }
}
