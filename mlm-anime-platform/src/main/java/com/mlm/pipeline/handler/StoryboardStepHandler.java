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
 * 【策略实现】拆分镜步骤处理器 — 对应 EpisodeStatus.STORYBOARD(4)
 * <p>
 * 剧本审核通过后自动触发，调用 AI（文生文）将剧本拆分为分镜脚本。
 * 每个分镜应包含：镜头号、画面描述、持续时间、运镜方式、角色位置。
 * <p>
 * AI 返回的分镜 JSON 通过 ModelGateway.saveResultToEpisode()
 * 自动回写到 episode.storyboardContent 字段，
 * 前端生成工作台从中读取。
 */
@Component
public class StoryboardStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(StoryboardStepHandler.class);
    private final ModelGateway modelGateway;

    public StoryboardStepHandler(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    @Override
    public EpisodeStatus step() { return EpisodeStatus.STORYBOARD; }

    @Override
    public void handle(Episode episode) {
        log.info(">>>>> STORYBOARD 开始: episodeId={}, projectId={}", episode.getId(), episode.getProjectId());

        GenerateRequest request = new GenerateRequest();
        request.setType(ModelType.TEXT_TO_TEXT);
        request.setVendor("openai");
        request.setPrompt(buildStoryboardPrompt(episode));
        request.setEpisodeId(episode.getId());

        modelGateway.generate(request);
        log.info("<<<<< STORYBOARD 提交完成: episodeId={}", episode.getId());
    }

    private String buildStoryboardPrompt(Episode episode) {
        return "你是一个专业的分镜师，请将以下剧本拆分为分镜脚本：\n" +
               "剧本：" + (episode.getScriptContent() != null ? episode.getScriptContent() : "无") + "\n" +
               "要求：每个分镜包含镜头号、画面描述、持续时间、运镜方式、角色位置。\n" +
               "输出格式为JSON数组。";
    }
}
