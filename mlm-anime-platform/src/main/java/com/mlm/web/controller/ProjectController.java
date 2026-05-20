package com.mlm.web.controller;

import com.mlm.common.dto.ApiResult;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.pipeline.engine.PipelineEngine;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.service.EpisodeService;
import com.mlm.pipeline.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目管理 REST 接口 — 项目 + 内部剧集的 Pipeline 控制
 * <p>
 * 流水线（Pipeline）是项目内部的功能，所有剧集操作都在项目上下文中完成。
 * <p>
 * 典型流程：
 * <pre>
 * 1. POST /api/projects              → 创建项目（可选引用资源，自动添加第一集）
 * 2. POST .../episodes               → 添加更多剧集
 * 3. POST .../episodes/{id}/submit-script   → 提交该集剧本 → 进入审核
 * 4. POST .../episodes/{id}/approve-script   → 剧本通过 → 自动拆分镜
 * 5. ... AI 异步处理中（2-5 分钟模拟延迟）...
 * 6. POST .../episodes/{id}/approve          → 终审通过 → 该集完成
 * </pre>
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;
    private final EpisodeService episodeService;
    private final PipelineEngine pipelineEngine;

    public ProjectController(ProjectService projectService,
                             EpisodeService episodeService,
                             PipelineEngine pipelineEngine) {
        this.projectService = projectService;
        this.episodeService = episodeService;
        this.pipelineEngine = pipelineEngine;
    }

    // ========== 项目级别 ==========

    /** 查询所有项目 */
    @GetMapping
    public ApiResult<List<Project>> list() {
        return ApiResult.ok(projectService.listAll());
    }

    /**
     * 查询项目详情（含剧集列表）
     * <p>
     * 返回 project 信息 + 所有 episodes 及其当前 Pipeline 状态
     */
    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> get(@PathVariable Long id) {
        Project project = projectService.getById(id);
        if (project == null) return ApiResult.fail(404, "项目不存在");
        List<Episode> episodes = episodeService.findByProjectId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("episodes", episodes);
        return ApiResult.ok(result);
    }

    /**
     * 创建项目（纯容器，不创建剧集）
     * <p>
     * 可直接创建或从资源库创建（传 resourceId）。
     * 创建后项目为空，所有剧集（包括第一集）通过 POST .../episodes 手动添加。
     *
     * @param name       项目名称
     * @param resourceId 可选，从资源库创建时传入资源 ID
     */
    @PostMapping
    public ApiResult<Project> create(@RequestParam String name,
                                     @RequestParam(required = false) Long resourceId) {
        Project project = pipelineEngine.createProject(name, resourceId);
        return ApiResult.ok(project);
    }

    // ========== 剧集管理（项目下） ==========

    /** 为项目添加新剧集 */
    @PostMapping("/{projectId}/episodes")
    public ApiResult<Episode> addEpisode(@PathVariable Long projectId,
                                         @RequestParam String title,
                                         @RequestParam Integer episodeNumber) {
        Episode episode = pipelineEngine.addEpisode(projectId, title, episodeNumber);
        return ApiResult.ok(episode);
    }

    /** 查询项目下某集详情 */
    @GetMapping("/{projectId}/episodes/{episodeId}")
    public ApiResult<Episode> getEpisode(@PathVariable Long projectId,
                                         @PathVariable Long episodeId) {
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId))
            return ApiResult.fail(404, "剧集不存在");
        return ApiResult.ok(episode);
    }

    // ========== Pipeline 操作（项目内剧集） ==========

    /**
     * 提交剧本 — 写入剧本内容后触发 Pipeline
     * <p>
     * 状态：SCRIPT_DRAFT → SCRIPT_REVIEW（等待人工审核剧本）
     */
    @PostMapping("/{projectId}/episodes/{episodeId}/submit-script")
    public ApiResult<?> submitScript(@PathVariable Long projectId,
                                     @PathVariable Long episodeId,
                                     @RequestBody String scriptContent) {
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId))
            return ApiResult.fail(404, "剧集不存在");

        episode.setScriptContent(scriptContent);
        pipelineEngine.submitScript(episode);
        log.info("提交剧本: projectId={}, episodeId={}, length={}", projectId, episodeId, scriptContent.length());
        return ApiResult.ok();
    }

    /**
     * 剧本审核通过 → 推进到 STORYBOARD（自动拆分镜）
     * <p>
     * 拆分镜由 AI 异步完成（模拟 2-5 分钟延迟）
     */
    @PostMapping("/{projectId}/episodes/{episodeId}/approve-script")
    public ApiResult<?> approveScript(@PathVariable Long projectId,
                                      @PathVariable Long episodeId) {
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId))
            return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.advance(episode);
        log.info("剧本审核通过: projectId={}, episodeId={}", projectId, episodeId);
        return ApiResult.ok();
    }

    /**
     * 剧本驳回 → 退回 SCRIPT_DRAFT 重写
     */
    @PostMapping("/{projectId}/episodes/{episodeId}/reject-script")
    public ApiResult<?> rejectScript(@PathVariable Long projectId,
                                     @PathVariable Long episodeId) {
        // 校验归属
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId))
            return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.reject(episodeId, EpisodeStatus.SCRIPT_DRAFT);
        log.info("剧本驳回: projectId={}, episodeId={}", projectId, episodeId);
        return ApiResult.ok();
    }

    /**
     * 终审通过 → 该集完成（COMPLETED）
     */
    @PostMapping("/{projectId}/episodes/{episodeId}/approve")
    public ApiResult<?> approve(@PathVariable Long projectId,
                                @PathVariable Long episodeId) {
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId))
            return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.advance(episode);
        log.info("终审通过: projectId={}, episodeId={}", projectId, episodeId);
        return ApiResult.ok();
    }

    /**
     * 终审驳回 → 退回 GENERATING 重做
     */
    @PostMapping("/{projectId}/episodes/{episodeId}/reject")
    public ApiResult<?> reject(@PathVariable Long projectId,
                               @PathVariable Long episodeId) {
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId))
            return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.reject(episodeId, EpisodeStatus.GENERATING);
        log.info("终审驳回: projectId={}, episodeId={}", projectId, episodeId);
        return ApiResult.ok();
    }

    /**
     * 重试失败步骤
     */
    @PostMapping("/{projectId}/episodes/{episodeId}/retry")
    public ApiResult<?> retry(@PathVariable Long projectId,
                              @PathVariable Long episodeId) {
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId))
            return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.retry(episodeId);
        log.info("重试: projectId={}, episodeId={}", projectId, episodeId);
        return ApiResult.ok();
    }
}
