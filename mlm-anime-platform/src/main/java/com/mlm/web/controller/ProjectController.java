package com.mlm.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.dto.ApiResult;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.pipeline.engine.PipelineEngine;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.entity.StageMember;
import com.mlm.pipeline.mapper.StageMemberMapper;
import com.mlm.pipeline.service.EpisodeService;
import com.mlm.pipeline.service.ProjectService;
import com.mlm.user.entity.User;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;
    private final EpisodeService episodeService;
    private final PipelineEngine pipelineEngine;
    private final StageMemberMapper stageMemberMapper;
    private final com.mlm.model.core.ModelGateway modelGateway;

    public ProjectController(ProjectService projectService,
                             EpisodeService episodeService,
                             PipelineEngine pipelineEngine,
                             StageMemberMapper stageMemberMapper,
                             com.mlm.model.core.ModelGateway modelGateway) {
        this.projectService = projectService;
        this.episodeService = episodeService;
        this.pipelineEngine = pipelineEngine;
        this.stageMemberMapper = stageMemberMapper;
        this.modelGateway = modelGateway;
    }

    // ========== 项目 ==========

    @GetMapping
    public ApiResult<List<Project>> list(HttpSession session) {
        User user = currentUser(session);
        List<Project> all = projectService.listAll();
        // 公开项目 + 自己的项目可见
        all.removeIf(p -> !p.getIsPublic() && !p.getCreatedBy().equals(user.getId()));
        return ApiResult.ok(all);
    }

    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> get(@PathVariable Long id, HttpSession session) {
        Project project = projectService.getById(id);
        if (project == null) return ApiResult.fail(404, "项目不存在");
        User user = currentUser(session);
        if (!project.getIsPublic() && !project.getCreatedBy().equals(user.getId()))
            return ApiResult.fail(403, "无权访问私有项目");

        List<Episode> episodes = episodeService.findByProjectId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("episodes", episodes);
        return ApiResult.ok(result);
    }

    @PostMapping
    public ApiResult<Project> create(@RequestParam String name,
                                     @RequestParam(required = false) Long resourceId,
                                     HttpSession session) {
        User user = currentUser(session);
        Project project = pipelineEngine.createProject(name, resourceId, user.getId());
        return ApiResult.ok(project);
    }

    /** 可见性切换 — 仅创建者可操作 */
    @PostMapping("/{id}/toggle-visibility")
    public ApiResult<?> toggleVisibility(@PathVariable Long id, HttpSession session) {
        User user = currentUser(session);
        Project project = projectService.getById(id);
        if (project == null) return ApiResult.fail(404, "项目不存在");
        if (!project.getCreatedBy().equals(user.getId())) return ApiResult.fail(403, "仅创建者可修改");

        project.setIsPublic(!project.getIsPublic());
        projectService.update(project);
        log.info("项目可见性切换: id={}, isPublic={}", id, project.getIsPublic());
        return ApiResult.ok();
    }

    // ========== 剧集管理 ==========

    @PostMapping("/{projectId}/episodes")
    public ApiResult<Episode> addEpisode(@PathVariable Long projectId,
                                         @RequestParam String title,
                                         @RequestParam Integer episodeNumber,
                                         HttpSession session) {
        checkStagePermission(projectId, EpisodeStatus.SCRIPT_DRAFT.getCode(), session);
        Episode episode = pipelineEngine.addEpisode(projectId, title, episodeNumber);
        return ApiResult.ok(episode);
    }

    // ========== 流水线操作（权限校验） ==========

    @PostMapping("/{projectId}/episodes/{episodeId}/submit-script")
    public ApiResult<?> submitScript(@PathVariable Long projectId,
                                     @PathVariable Long episodeId,
                                     @RequestBody String scriptContent,
                                     HttpSession session) {
        checkStagePermission(projectId, EpisodeStatus.SCRIPT_DRAFT.getCode(), session);
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId)) return ApiResult.fail(404, "剧集不存在");
        episode.setScriptContent(scriptContent);
        pipelineEngine.submitScript(episode);
        return ApiResult.ok();
    }

    @PostMapping("/{projectId}/episodes/{episodeId}/approve-script")
    public ApiResult<?> approveScript(@PathVariable Long projectId,
                                      @PathVariable Long episodeId,
                                      HttpSession session) {
        checkStagePermission(projectId, EpisodeStatus.SCRIPT_REVIEW.getCode(), session);
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId)) return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.advance(episode);
        return ApiResult.ok();
    }

    @PostMapping("/{projectId}/episodes/{episodeId}/reject-script")
    public ApiResult<?> rejectScript(@PathVariable Long projectId,
                                     @PathVariable Long episodeId,
                                     HttpSession session) {
        checkStagePermission(projectId, EpisodeStatus.SCRIPT_REVIEW.getCode(), session);
        pipelineEngine.reject(episodeId, EpisodeStatus.SCRIPT_DRAFT);
        return ApiResult.ok();
    }

    @PostMapping("/{projectId}/episodes/{episodeId}/approve")
    public ApiResult<?> approve(@PathVariable Long projectId,
                                @PathVariable Long episodeId,
                                HttpSession session) {
        checkStagePermission(projectId, EpisodeStatus.EPISODE_APPROVAL.getCode(), session);
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId)) return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.advance(episode);
        return ApiResult.ok();
    }

    @PostMapping("/{projectId}/episodes/{episodeId}/reject")
    public ApiResult<?> reject(@PathVariable Long projectId,
                               @PathVariable Long episodeId,
                               HttpSession session) {
        checkStagePermission(projectId, EpisodeStatus.EPISODE_APPROVAL.getCode(), session);
        pipelineEngine.reject(episodeId, EpisodeStatus.GENERATING);
        return ApiResult.ok();
    }

    @PostMapping("/{projectId}/episodes/{episodeId}/retry")
    public ApiResult<?> retry(@PathVariable Long projectId,
                              @PathVariable Long episodeId,
                              HttpSession session) {
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId)) return ApiResult.fail(404, "剧集不存在");
        // 重试仅创建者或对应阶段负责人可操作
        checkStagePermission(projectId, episode.getStatus().getCode(), session);
        pipelineEngine.retry(episodeId);
        return ApiResult.ok();
    }

    // ========== 阶段负责人 ==========

    @PutMapping("/{projectId}/stage-members")
    public ApiResult<?> setStageMembers(@PathVariable Long projectId,
                                        @RequestBody List<StageMember> members,
                                        HttpSession session) {
        User user = currentUser(session);
        Project project = projectService.getById(projectId);
        if (project == null) return ApiResult.fail(404, "项目不存在");
        if (!project.getCreatedBy().equals(user.getId())) return ApiResult.fail(403, "仅创建者可设置");

        stageMemberMapper.delete(new LambdaQueryWrapper<StageMember>().eq(StageMember::getProjectId, projectId));
        for (StageMember m : members) {
            m.setProjectId(projectId);
            stageMemberMapper.insert(m);
        }
        return ApiResult.ok();
    }

    // ========== 展示：分镜 & 成片 ==========

    // ========== AI 成片手动生成 ==========

    /** 生成指定场景的图片 */
    @PostMapping("/{projectId}/episodes/{episodeId}/generate-image")
    public ApiResult<?> generateImage(@PathVariable Long projectId,
                                       @PathVariable Long episodeId,
                                       @RequestParam(defaultValue = "0") int sceneIndex,
                                       @RequestParam(defaultValue = "") String prompt,
                                       HttpSession session) {
        checkStagePermission(projectId, EpisodeStatus.GENERATING.getCode(), session);
        Episode episode = episodeService.getById(episodeId);
        if (episode == null) return ApiResult.fail(404, "剧集不存在");

        com.mlm.model.core.GenerateRequest req = new com.mlm.model.core.GenerateRequest();
        req.setType(com.mlm.common.enums.ModelType.TEXT_TO_IMAGE);
        req.setVendor("stable_diffusion");
        req.setPrompt(prompt.isBlank() ? ("分镜" + (sceneIndex + 1)) : prompt);
        req.setEpisodeId(episodeId);
        req.setWidth(1920);
        req.setHeight(1080);
        modelGateway.generate(req);
        return ApiResult.ok();
    }

    /** 生成视频（基于已生成的图片） */
    @PostMapping("/{projectId}/episodes/{episodeId}/generate-video")
    public ApiResult<?> generateVideo(@PathVariable Long projectId,
                                       @PathVariable Long episodeId,
                                       @RequestParam(required = false) String imageUrl,
                                       HttpSession session) {
        checkStagePermission(projectId, EpisodeStatus.GENERATING.getCode(), session);

        com.mlm.model.core.GenerateRequest req = new com.mlm.model.core.GenerateRequest();
        req.setType(com.mlm.common.enums.ModelType.IMAGE_TO_VIDEO);
        req.setVendor("kling");
        req.setReferenceImageUrl(imageUrl != null ? imageUrl : "https://example.com/default.jpg");
        req.setEpisodeId(episodeId);
        modelGateway.generate(req);
        return ApiResult.ok();
    }

    /** 确认成片完成 → 推进到 EPISODE_APPROVAL */
    @PostMapping("/{projectId}/episodes/{episodeId}/complete-generation")
    public ApiResult<?> completeGeneration(@PathVariable Long projectId,
                                            @PathVariable Long episodeId,
                                            HttpSession session) {
        checkStagePermission(projectId, EpisodeStatus.GENERATING.getCode(), session);
        Episode episode = episodeService.getById(episodeId);
        if (episode == null) return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.advance(episode);
        return ApiResult.ok();
    }

    /** 获取剧集生成结果（图片列表） — 演示版返回固定 URL */
    @GetMapping("/{projectId}/episodes/{episodeId}/results")
    public ApiResult<List<Map<String, String>>> getResults(@PathVariable Long projectId,
                                                            @PathVariable Long episodeId) {
        var results = List.of(
            Map.of("url", "https://picsum.photos/seed/" + episodeId + "a/1920/1080", "label", "分镜1"),
            Map.of("url", "https://picsum.photos/seed/" + episodeId + "b/1920/1080", "label", "分镜2"),
            Map.of("url", "https://picsum.photos/seed/" + episodeId + "c/1920/1080", "label", "分镜3"),
            Map.of("url", "https://picsum.photos/seed/" + episodeId + "d/1920/1080", "label", "分镜4")
        );
        return ApiResult.ok(results);
    }

    @GetMapping("/{projectId}/stage-members")
    public ApiResult<List<StageMember>> getStageMembers(@PathVariable Long projectId) {
        return ApiResult.ok(stageMemberMapper.selectList(
            new LambdaQueryWrapper<StageMember>().eq(StageMember::getProjectId, projectId)));
    }

    // ========== 工具 ==========

    private User currentUser(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) throw new RuntimeException("未登录");
        return user;
    }

    private void checkStagePermission(Long projectId, int stageCode, HttpSession session) {
        User user = currentUser(session);
        Project project = projectService.getById(projectId);
        if (project == null) throw new RuntimeException("项目不存在");

        // 创建者始终有全部权限
        if (project.getCreatedBy().equals(user.getId())) return;

        // 查询该阶段是否有指定的负责人
        StageMember member = stageMemberMapper.selectOne(
            new LambdaQueryWrapper<StageMember>()
                .eq(StageMember::getProjectId, projectId)
                .eq(StageMember::getStage, stageCode)
        );
        if (member != null && !member.getUserId().equals(user.getId()))
            throw new RuntimeException("无权操作该阶段");
    }
}
