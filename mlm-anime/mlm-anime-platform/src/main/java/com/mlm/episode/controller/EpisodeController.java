package com.mlm.episode.controller;

import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.exception.BizException;
import com.mlm.common.result.ApiResult;
import com.mlm.common.util.AuthContext;
import com.mlm.common.util.StagePermissionUtil;
import com.mlm.episode.dto.EpisodeAddRequest;
import com.mlm.episode.dto.EpisodeIdRequest;
import com.mlm.episode.dto.ScriptSubmitRequest;
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

/**
 * 剧集管理控制器 — 剧集的生命周期操作。
 */
@RestController
@RequestMapping("/api/projects/episode")
public class EpisodeController {

    private static final Logger log = LoggerFactory.getLogger(EpisodeController.class);

    private final EpisodeService episodeService;
    private final ProjectService projectService;
    private final PipelineEngine pipelineEngine;
    private final StageMemberMapper stageMemberMapper;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://localhost:9000}")
    private String authServiceUrl;

    public EpisodeController(EpisodeService episodeService,
                             ProjectService projectService,
                             PipelineEngine pipelineEngine,
                             StageMemberMapper stageMemberMapper,
                             RestTemplate restTemplate) {
        this.episodeService = episodeService;
        this.projectService = projectService;
        this.pipelineEngine = pipelineEngine;
        this.stageMemberMapper = stageMemberMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * 添加剧集 — 在指定项目下创建新剧集。
     */
    @PostMapping("/add")
    public ApiResult<Episode> addEpisode(@RequestBody EpisodeAddRequest req,
                                         HttpServletRequest request) {
        if (req == null || req.getProjectId() == null) {
            throw new BizException(400, "项目ID不能为空");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.SCRIPT_DRAFT.getCode(),
                userId, projectService, stageMemberMapper);

        Episode episode = episodeService.addEpisode(
                req.getProjectId(),
                req.getTitle(),
                req.getEpisodeNumber() != null ? req.getEpisodeNumber() : 1
        );
        if (episode == null) {
            throw new BizException(404, "项目不存在，无法添加剧集", "PROJECT_NOT_FOUND");
        }
        return ApiResult.ok(episode);
    }

    /**
     * 提交剧本 — 保存并自动推进到审核状态。
     */
    @PostMapping("/submit-script")
    public ApiResult<String> submitScript(@RequestBody ScriptSubmitRequest req,
                                          HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            throw new BizException(400, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.SCRIPT_DRAFT.getCode(),
                userId, projectService, stageMemberMapper);

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            throw new BizException(404, "剧集不存在", "EPISODE_NOT_FOUND");
        }

        episode.setScriptContent(req.getScriptContent());
        pipelineEngine.submitScript(episode);
        return ApiResult.ok("SCRIPT_SUBMITTED");
    }

    /**
     * 剧本审核通过 — 自动推进到 STORYBOARD 并触发 AI 拆分镜。
     */
    @PostMapping("/approve-script")
    public ApiResult<String> approveScript(@RequestBody EpisodeIdRequest req,
                                           HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            throw new BizException(400, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.SCRIPT_REVIEW.getCode(),
                userId, projectService, stageMemberMapper);

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            throw new BizException(404, "剧集不存在", "EPISODE_NOT_FOUND");
        }

        pipelineEngine.advance(episode);
        return ApiResult.ok("SCRIPT_APPROVED");
    }

    /**
     * 剧本驳回 — 退回 SCRIPT_DRAFT 重做。
     */
    @PostMapping("/reject-script")
    public ApiResult<String> rejectScript(@RequestBody EpisodeIdRequest req,
                                          HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            throw new BizException(400, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.SCRIPT_REVIEW.getCode(),
                userId, projectService, stageMemberMapper);

        pipelineEngine.reject(req.getEpisodeId(), EpisodeStatus.SCRIPT_DRAFT);
        return ApiResult.ok("SCRIPT_REJECTED");
    }

    /**
     * 终审通过 — 剧集进入 COMPLETED 完成状态。
     */
    @PostMapping("/approve")
    public ApiResult<String> approve(@RequestBody EpisodeIdRequest req,
                                     HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            throw new BizException(400, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.EPISODE_APPROVAL.getCode(),
                userId, projectService, stageMemberMapper);

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            throw new BizException(404, "剧集不存在", "EPISODE_NOT_FOUND");
        }

        pipelineEngine.advance(episode);
        return ApiResult.ok("FINAL_APPROVED");
    }

    /**
     * 终审驳回 — 退回 GENERATING 重做 AI 生成。
     */
    @PostMapping("/reject")
    public ApiResult<String> reject(@RequestBody EpisodeIdRequest req,
                                    HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            throw new BizException(400, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.EPISODE_APPROVAL.getCode(),
                userId, projectService, stageMemberMapper);

        pipelineEngine.reject(req.getEpisodeId(), EpisodeStatus.GENERATING);
        return ApiResult.ok("FINAL_REJECTED");
    }

    /**
     * 重试失败步骤。
     */
    @PostMapping("/retry")
    public ApiResult<String> retry(@RequestBody EpisodeIdRequest req,
                                   HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            throw new BizException(400, "参数不完整");
        }

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            throw new BizException(404, "剧集不存在", "EPISODE_NOT_FOUND");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), episode.getStatus(),
                userId, projectService, stageMemberMapper);

        pipelineEngine.retry(req.getEpisodeId());
        return ApiResult.ok("EPISODE_RETRIED");
    }

    private Long currentUserId(HttpServletRequest request) {
        return AuthContext.currentUserId(request, restTemplate, authServiceUrl);
    }
}
