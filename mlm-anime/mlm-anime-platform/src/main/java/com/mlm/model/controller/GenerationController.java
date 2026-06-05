package com.mlm.model.controller;

import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.ModelType;
import com.mlm.common.exception.BizException;
import com.mlm.common.result.ApiResult;
import com.mlm.common.util.AuthContext;
import com.mlm.common.util.StagePermissionUtil;
import com.mlm.model.core.GenerateRequest;
import com.mlm.model.core.ModelGateway;
import com.mlm.model.dto.GenerateImageRequest;
import com.mlm.model.dto.GenerateVideoRequest;
import com.mlm.model.dto.GenerationResultVO;
import com.mlm.model.dto.ResultsRequest;
import com.mlm.pipeline.engine.PipelineEngine;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.mapper.StageMemberMapper;
import com.mlm.pipeline.service.EpisodeService;
import com.mlm.pipeline.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * AI 生成控制器 — 文生图、图生视频和生成结果查询。
 */
@RestController
@RequestMapping("/api/projects/episode")
public class GenerationController {

    private static final Logger log = LoggerFactory.getLogger(GenerationController.class);

    private final ProjectService projectService;
    private final EpisodeService episodeService;
    private final ModelGateway modelGateway;
    private final PipelineEngine pipelineEngine;
    private final StageMemberMapper stageMemberMapper;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://localhost:9000}")
    private String authServiceUrl;

    private static final int DEFAULT_IMAGE_WIDTH = 1920;
    private static final int DEFAULT_IMAGE_HEIGHT = 1080;
    private static final String DEFAULT_REFERENCE_IMAGE = "https://example.com/default.jpg";

    public GenerationController(ProjectService projectService,
                                EpisodeService episodeService,
                                ModelGateway modelGateway,
                                PipelineEngine pipelineEngine,
                                StageMemberMapper stageMemberMapper,
                                RestTemplate restTemplate) {
        this.projectService = projectService;
        this.episodeService = episodeService;
        this.modelGateway = modelGateway;
        this.pipelineEngine = pipelineEngine;
        this.stageMemberMapper = stageMemberMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * 生成图片（文生图）— 调用 Stable Diffusion。
     */
    @PostMapping("/generate-image")
    public ApiResult<String> generateImage(@RequestBody GenerateImageRequest req,
                                           HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            throw new BizException(400, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.GENERATING.getCode(),
                userId, projectService, stageMemberMapper);

        GenerateRequest genReq = new GenerateRequest();
        genReq.setType(ModelType.TEXT_TO_IMAGE);
        genReq.setVendor("stable_diffusion");
        genReq.setPrompt(req.getPrompt() != null ? req.getPrompt() : "");
        genReq.setEpisodeId(req.getEpisodeId());
        genReq.setWidth(DEFAULT_IMAGE_WIDTH);
        genReq.setHeight(DEFAULT_IMAGE_HEIGHT);

        modelGateway.generate(genReq);
        return ApiResult.ok("IMAGE_GENERATION_STARTED");
    }

    /**
     * 生成视频（图生视频）— 调用可灵 Kling。
     */
    @PostMapping("/generate-video")
    public ApiResult<String> generateVideo(@RequestBody GenerateVideoRequest req,
                                           HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            throw new BizException(400, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.GENERATING.getCode(),
                userId, projectService, stageMemberMapper);

        GenerateRequest genReq = new GenerateRequest();
        genReq.setType(ModelType.IMAGE_TO_VIDEO);
        genReq.setVendor("kling");
        genReq.setReferenceImageUrl(req.getImageUrl() != null
                ? req.getImageUrl() : DEFAULT_REFERENCE_IMAGE);
        genReq.setEpisodeId(req.getEpisodeId());

        modelGateway.generate(genReq);
        return ApiResult.ok("VIDEO_GENERATION_STARTED");
    }

    /**
     * 完成 AI 生成 → 推进到终审。
     */
    @PostMapping("/complete-generation")
    public ApiResult<String> completeGeneration(@RequestBody com.mlm.episode.dto.EpisodeIdRequest req,
                                                HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            throw new BizException(400, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.GENERATING.getCode(),
                userId, projectService, stageMemberMapper);

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null) {
            throw new BizException(404, "剧集不存在", "EPISODE_NOT_FOUND");
        }

        pipelineEngine.advance(episode);
        return ApiResult.ok("GENERATION_COMPLETED");
    }

    /**
     * 查询剧集的生成结果。
     */
    @PostMapping("/results")
    public ApiResult<GenerationResultVO> getResults(@RequestBody ResultsRequest req) {
        if (req == null || req.getEpisodeId() == null) {
            throw new BizException(400, "剧集ID不能为空");
        }

        // TODO: 从 task 表查询实际生成结果
        Long episodeId = req.getEpisodeId();
        GenerationResultVO result = GenerationResultVO.builder()
                .items(Arrays.asList(
                        GenerationResultVO.GenerationItemVO.builder()
                                .url("https://picsum.photos/seed/" + episodeId + "a/960/540")
                                .label("分镜1").build(),
                        GenerationResultVO.GenerationItemVO.builder()
                                .url("https://picsum.photos/seed/" + episodeId + "b/960/540")
                                .label("分镜2").build()
                ))
                .build();

        return ApiResult.ok(result);
    }

    private Long currentUserId(HttpServletRequest request) {
        return AuthContext.currentUserId(request, restTemplate, authServiceUrl);
    }
}
