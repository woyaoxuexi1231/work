package com.mlm.web.controller;

import com.mlm.common.constant.ErrorCode;
import com.mlm.common.dto.ApiResult;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.util.AuthContext;
import com.mlm.common.util.StagePermissionUtil;
import com.mlm.pipeline.engine.PipelineEngine;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.mapper.StageMemberMapper;
import com.mlm.pipeline.service.EpisodeService;
import com.mlm.pipeline.service.ProjectService;
import com.mlm.web.dto.EpisodeAddRequest;
import com.mlm.web.dto.EpisodeIdRequest;
import com.mlm.web.dto.ScriptSubmitRequest;
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
 * 剧集管理控制器 — 剧集的生命周期操作
 * <p>
 * 【职责】
 * <ul>
 *   <li>剧集创建（add）— 在项目中添加新剧集</li>
 *   <li>剧本操作（submit-script / approve-script / reject-script）— 剧本提交审核与流转</li>
 *   <li>终审操作（approve / reject）— 成片终审通过与驳回</li>
 *   <li>重试操作（retry）— 失败步骤的手动重试</li>
 * </ul>
 * <p>
 * 【Pipeline 流转】
 * 剧集独立走完完整的 Pipeline 管线，状态在以下值间流转：
 * SCRIPT_DRAFT(2) → SCRIPT_REVIEW(3) → STORYBOARD(4) → GENERATING(5)
 * → EPISODE_APPROVAL(6) → COMPLETED(7)
 *
 * @author mlm
 * @see PipelineEngine 状态机引擎
 * @see com.mlm.web.controller.GenerationController AI 生成操作
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

    /**
     * 构造剧集管理控制器
     */
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
     * 添加剧集
     * <p>
     * 在指定项目下创建新剧集，自动增加项目的总集数计数。
     *
     * @param req     创建请求（项目 ID、标题、集号）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 创建后的剧集实体
     */
    @PostMapping("/add")
    public ApiResult<Episode> addEpisode(@RequestBody EpisodeAddRequest req,
                                         HttpServletRequest request) {
        if (req == null || req.getProjectId() == null) {
            return ApiResult.fail(ErrorCode.INVALID_REQUEST, "项目ID不能为空");
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
            return ApiResult.fail(ErrorCode.PROJECT_NOT_FOUND, "项目不存在，无法添加剧集");
        }
        log.info("剧集已添加: id={}, projectId={}, episodeNumber={}",
                episode.getId(), req.getProjectId(), episode.getEpisodeNumber());
        return ApiResult.ok(episode);
    }

    /**
     * 提交剧本
     * <p>
     * 用户提交剧本内容后，系统将其保存到剧集实体并自动推进到审核状态。
     *
     * @param req     请求（项目 ID、剧集 ID、剧本内容）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 操作成功状态
     */
    @PostMapping("/submit-script")
    public ApiResult<String> submitScript(@RequestBody ScriptSubmitRequest req,
                                          HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.EPISODE_PARAMS_INCOMPLETE, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.SCRIPT_DRAFT.getCode(),
                userId, projectService, stageMemberMapper);

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            return ApiResult.fail(ErrorCode.EPISODE_NOT_FOUND, "剧集不存在");
        }

        episode.setScriptContent(req.getScriptContent());
        pipelineEngine.submitScript(episode);
        log.info("剧本已提交: episodeId={}, projectId={}", req.getEpisodeId(), req.getProjectId());
        return ApiResult.ok("SCRIPT_SUBMITTED");
    }

    /**
     * 剧本审核通过
     * <p>
     * 审核通过后，系统自动推进剧集到 STORYBOARD(4) 状态并触发 AI 拆分镜。
     *
     * @param req     请求（项目 ID、剧集 ID）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 操作成功状态
     */
    @PostMapping("/approve-script")
    public ApiResult<String> approveScript(@RequestBody EpisodeIdRequest req,
                                           HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.EPISODE_PARAMS_INCOMPLETE, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.SCRIPT_REVIEW.getCode(),
                userId, projectService, stageMemberMapper);

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            return ApiResult.fail(ErrorCode.EPISODE_NOT_FOUND, "剧集不存在");
        }

        pipelineEngine.advance(episode);
        log.info("剧本审核通过: episodeId={}, projectId={}", req.getEpisodeId(), req.getProjectId());
        return ApiResult.ok("SCRIPT_APPROVED");
    }

    /**
     * 剧本驳回
     * <p>
     * 驳回后剧集退回 SCRIPT_DRAFT(2) 状态，用户可修改后重新提交。
     *
     * @param req     请求（项目 ID、剧集 ID）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 操作成功状态
     */
    @PostMapping("/reject-script")
    public ApiResult<String> rejectScript(@RequestBody EpisodeIdRequest req,
                                          HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.EPISODE_PARAMS_INCOMPLETE, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.SCRIPT_REVIEW.getCode(),
                userId, projectService, stageMemberMapper);

        pipelineEngine.reject(req.getEpisodeId(), EpisodeStatus.SCRIPT_DRAFT);
        log.info("剧本已驳回: episodeId={}, projectId={}", req.getEpisodeId(), req.getProjectId());
        return ApiResult.ok("SCRIPT_REJECTED");
    }

    /**
     * 终审通过
     * <p>
     * 终审通过后剧集进入 COMPLETED(7) 完成状态。
     *
     * @param req     请求（项目 ID、剧集 ID）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 操作成功状态
     */
    @PostMapping("/approve")
    public ApiResult<String> approve(@RequestBody EpisodeIdRequest req,
                                     HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.EPISODE_PARAMS_INCOMPLETE, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.EPISODE_APPROVAL.getCode(),
                userId, projectService, stageMemberMapper);

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            return ApiResult.fail(ErrorCode.EPISODE_NOT_FOUND, "剧集不存在");
        }

        pipelineEngine.advance(episode);
        log.info("终审通过: episodeId={}, projectId={}", req.getEpisodeId(), req.getProjectId());
        return ApiResult.ok("FINAL_APPROVED");
    }

    /**
     * 终审驳回
     * <p>
     * 驳回后剧集退回 GENERATING(5) 状态，用户可调整后重新提交终审。
     *
     * @param req     请求（项目 ID、剧集 ID）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 操作成功状态
     */
    @PostMapping("/reject")
    public ApiResult<String> reject(@RequestBody EpisodeIdRequest req,
                                    HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.EPISODE_PARAMS_INCOMPLETE, "参数不完整");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), EpisodeStatus.EPISODE_APPROVAL.getCode(),
                userId, projectService, stageMemberMapper);

        pipelineEngine.reject(req.getEpisodeId(), EpisodeStatus.GENERATING);
        log.info("终审已驳回: episodeId={}, projectId={}", req.getEpisodeId(), req.getProjectId());
        return ApiResult.ok("FINAL_REJECTED");
    }

    /**
     * 重试失败步骤
     * <p>
     * 将当前剧集中失败的步骤重置并重新执行。
     *
     * @param req     请求（项目 ID、剧集 ID）
     * @param request HTTP 请求（用于阶段权限校验）
     * @return 操作成功状态
     */
    @PostMapping("/retry")
    public ApiResult<String> retry(@RequestBody EpisodeIdRequest req,
                                   HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(ErrorCode.EPISODE_PARAMS_INCOMPLETE, "参数不完整");
        }

        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            return ApiResult.fail(ErrorCode.EPISODE_NOT_FOUND, "剧集不存在");
        }

        Long userId = currentUserId(request);
        StagePermissionUtil.checkStagePermission(
                req.getProjectId(), episode.getStatus(),
                userId, projectService, stageMemberMapper);

        pipelineEngine.retry(req.getEpisodeId());
        log.info("重试触发: episodeId={}", req.getEpisodeId());
        return ApiResult.ok("EPISODE_RETRIED");
    }

    /**
     * 从 HTTP 请求中提取当前用户 ID
     */
    private Long currentUserId(HttpServletRequest request) {
        return AuthContext.currentUserId(request, restTemplate, authServiceUrl);
    }
}
