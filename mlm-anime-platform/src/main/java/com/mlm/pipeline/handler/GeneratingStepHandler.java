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

import java.util.List;

/**
 * AI 成片步骤处理器 — GENERATING
 * <p>
 * 分镜完成后自动触发，遍历分镜列表依次：
 * <ol>
 *   <li>文生图（Stable Diffusion）— 每个分镜生成一张画面</li>
 *   <li>图生视频（Kling）— 将生成的图片转为视频片段</li>
 *   <li>全部完成后推进到 EPISODE_APPROVAL</li>
 * </ol>
 */
@Component
public class GeneratingStepHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(GeneratingStepHandler.class);
    private final ModelGateway modelGateway;

    public GeneratingStepHandler(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    @Override
    public EpisodeStatus step() { return EpisodeStatus.GENERATING; }

    @Override
    public void handle(Episode episode) {
        log.info(">>>>> GENERATING 开始: episodeId={}, projectId={}", episode.getId(), episode.getProjectId());

        // 从分镜内容中解析场景列表
        List<String> scenes = parseScenes(episode);

        // 每个分镜生成一张图
        for (int i = 0; i < scenes.size(); i++) {
            GenerateRequest imgRequest = new GenerateRequest();
            imgRequest.setType(ModelType.TEXT_TO_IMAGE);
            imgRequest.setVendor("stable_diffusion");
            imgRequest.setPrompt(scenes.get(i));
            imgRequest.setWidth(1920);
            imgRequest.setHeight(1080);
            imgRequest.setEpisodeId(episode.getId());
            modelGateway.generate(imgRequest);
            log.debug("分镜{} 文生图已提交: scene={}", i + 1, scenes.get(i));
        }

        // 等所有图生成后提交图生视频（模拟：仅做示意）
        log.info("<<<<< GENERATING 提交完成: episodeId={}, 共{}个分镜", episode.getId(), scenes.size());
    }

    /** 解析分镜场景列表 — TODO: 从 STORYBOARD 的结果 JSON 中提取实际分镜列表 */
    private List<String> parseScenes(Episode episode) {
        return List.of("开场全景", "角色特写", "动作场景", "结尾定格");
    }
}
