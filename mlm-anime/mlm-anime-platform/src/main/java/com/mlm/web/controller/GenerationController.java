package com.mlm.web.controller;

import com.mlm.common.constant.ErrorCode;
import com.mlm.common.dto.ApiResult;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.ModelType;
import com.mlm.common.util.AuthContext;
import com.mlm.common.util.StagePermissionUtil;
import com.mlm.model.core.GenerateRequest;
import com.mlm.model.core.ModelGateway;
import com.mlm.pipeline.engine.PipelineEngine;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.mapper.StageMemberMapper;
import com.mlm.pipeline.service.EpisodeService;
import com.mlm.pipeline.service.ProjectService;
import com.mlm.web.dto.EpisodeIdRequest;
import com.mlm.web.dto.GenerateImageRequest;
import com.mlm.web.dto.GenerateVideoRequest;
import com.mlm.web.dto.GenerationResultVO;
import com.mlm.web.dto.ResultsRequest;
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
 * AI 生成控制器 — 文生图、图生视频和生成结果查询
 * <p>
 * 【职责】
 * <ul>
 *   <li>生成图片（generate-image）— 调用 Stable Diffusion 文生图</li>
 *   <li>生成视频（generate-video）— 调用可灵 Kling 图生视频</li>
 *   <li>推进终审（complete-generation）— AI 成片完成后手动触发推进</li>
 *   <li>生成结果（results）— 查询当前剧集的生成产物</li>
 * </ul>
 * <p>
 * 【设计说明】
 * AI 生成是<em>用户手动操作</em>的场景。用户在生成工作台查看分镜列表后，
 * 逐场景触发图片和视频生成，所有生成任务完成后点击「提交终审」。
 *
 * @author mlm
 * @see EpisodeController 剧集生命周期管理
 * @see ModelGateway AI 模型调用网关
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

    /** 默认生成参数 */
    private static final int DEFAULT_IMAGE_WIDTH = 1920;
    private static final int DEFAULT_IMAGE_HEIGHT = 1080;
    private static final String DEFAULT_REFERENCE_IMAGE = "https://example.com/default.jpg";

    /**
     * 构造 AI 生成控制器
     */
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
     * 生成图片（文生图）
     * <p>
     * 调用 Stable Diffusion 从文字描述生成场景图片。
     * 默认分辨率 1920×1080，异步完成后通过轮询获取结果。
     *
     * @param req     生成请求（项目 ID、剧集 ID、提示词）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 操作成功状态
     */
    @PostMapping("/generate-image")
    public ApiResult<String> generateImage(@RequestBody GenerateImageRequest req,
                                           HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.EPISODE_PARAMS_INCOMPLETE, "参数不完整");
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
        log.info("图片生成已提交: episodeId={}, prompt={}",
                req.getEpisodeId(), truncate(req.getPrompt(), 50));
        return ApiResult.ok("IMAGE_GENERATION_STARTED");
    }

    /**
     * 生成视频（图生视频）
     * <p>
     * 调用可灵 Kling 从参考图生成视频。
     * 异步完成后通过轮询获取结果。
     *
     * @param req     生成请求（项目 ID、剧集 ID、图片 URL）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 操作成功状态
     */
    @PostMapping("/generate-video")
    public ApiResult<String> generateVideo(@RequestBody GenerateVideoRequest req,
                                           HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.EPISODE_PARAMS_INCOMPLETE, "参数不完整");
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
        log.info("视频生成已提交: episodeId={}", req.getEpisodeId());
        return ApiResult.ok("VIDEO_GENERATION_STARTED");
    }

    /**
     * 完成 AI 生成 → 推进到终审
     * <p>
     * 用户确认所有图片和视频生成完成后调用此接口，
     * 将剧集从 GENERATING(5) 推进到 EPISODE_APPROVAL(6) 等待终审。
     *
     * @param req     请求（项目 ID、剧集 ID）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 操作成功状态
     */
    @PostMapping("/complete-generation")
    public ApiResult<String> completeGeneration(@RequestBody EpisodeIdRequest req,
                                                HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.EPISODE_PARAMS_INCOMPLETE, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.GENERATING.getCode(),
                userId, projectService, stageMemberMapper);

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null) {
            return ApiResult.fail(ErrorCode.EPISODE_NOT_FOUND, "剧集不存在");
        }

        pipelineEngine.advance(episode);
        log.info("AI 生成完成，已推进终审: episodeId={}", req.getEpisodeId());
        return ApiResult.ok("GENERATION_COMPLETED");
    }

    /**
     * 查询剧集的生成结果
     * <p>
     * 返回当前剧集所有 AI 生成的图片/视频产物列表。
     * <p>
     * TODO: 当前返回模拟数据，需对接真实数据库查询。
     *
     * @param req 请求（剧集 ID）
     * @return 生成结果列表（含 URL 和标签）
     */
    @PostMapping("/results")
    public ApiResult<GenerationResultVO> getResults(@RequestBody ResultsRequest req) {
        if (req == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.INVALID_REQUEST, "剧集ID不能为空");
        }

        // TODO: 从 task 表查询实际生成结果，当前返回模拟数据
        Long episodeId = req.getEpisodeId();
        GenerationResultVO result = GenerationResultVO.builder()
                .items(Arrays.asList(
                        GenerationResultVO.GenerationItemVO.builder()
                                .url("https://picsum.photos/seed/" + episodeId + "a/960/540")
                                .label("分镜1").build(),
                        GenerationResultVO.GenerationItemVO.builder()
                                .url("https://picsum.photos/seed/" + episodeId + "b/960/540")
                                .label("分镜2").build(),
                        GenerationResultVO.GenerationItemVO.builder()
                                .url("https://picsum.photos/seed/" + episodeId + "c/960/540")
                                .label("分镜3").build(),
                        GenerationResultVO.GenerationItemVO.builder()
                                .url("https://picsum.photos/seed/" + episodeId + "d/960/540")
                                .label("分镜4").build()
                ))
                .build();

        log.debug("生成结果查询: episodeId={}, itemsCount={}", episodeId, result.getItems().size());
        return ApiResult.ok(result);
    }

    /**
     * 从 HTTP 请求中提取当前用户 ID
     */
    private Long currentUserId(HttpServletRequest request) {
        return AuthContext.currentUserId(request, restTemplate, authServiceUrl);
    }

    /**
     * 截断字符串到指定长度（用于日志显示）
     */
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
