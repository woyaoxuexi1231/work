package com.mlm.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.dto.ApiResult;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.model.core.GenerateRequest;
import com.mlm.model.core.ModelGateway;
import com.mlm.pipeline.engine.PipelineEngine;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.entity.StageMember;
import com.mlm.pipeline.mapper.StageMemberMapper;
import com.mlm.pipeline.service.EpisodeService;
import com.mlm.pipeline.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目管理接口 — 全部 POST + body 对象
 * <p>
 * 统一用 POST 请求，路径无变量，参数全在 JSON body 中。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;
    private final EpisodeService episodeService;
    private final PipelineEngine pipelineEngine;
    private final StageMemberMapper stageMemberMapper;
    private final ModelGateway modelGateway;
    private final RestTemplate restTemplate = new RestTemplate();

    public ProjectController(ProjectService projectService, EpisodeService episodeService,
                             PipelineEngine pipelineEngine, StageMemberMapper stageMemberMapper,
                             ModelGateway modelGateway) {
        this.projectService = projectService;
        this.episodeService = episodeService;
        this.pipelineEngine = pipelineEngine;
        this.stageMemberMapper = stageMemberMapper;
        this.modelGateway = modelGateway;
    }

    /** 项目列表 */
    @PostMapping("/list")
    public ApiResult<List<Project>> list(HttpServletRequest request) {
        Long userId = currentUserId(request);
        List<Project> all = projectService.listAll();
        all.removeIf(p -> !p.getIsPublic() && !p.getCreatedBy().equals(userId));
        return ApiResult.ok(all);
    }

    /** 项目详情（含剧集列表） */
    @PostMapping("/get")
    public ApiResult<Map<String, Object>> get(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        Long id = body.get("id");
        Project project = projectService.getById(id);
        if (project == null) return ApiResult.fail(404, "项目不存在");
        Long userId = currentUserId(request);
        if (!project.getIsPublic() && !project.getCreatedBy().equals(userId))
            return ApiResult.fail(403, "无权访问");
        List<Episode> episodes = episodeService.findByProjectId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("episodes", episodes);
        return ApiResult.ok(result);
    }

    /** 创建项目 */
    @PostMapping("/create")
    public ApiResult<Project> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String name = (String) body.get("name");
        Long resourceId = body.get("resourceId") != null ? ((Number) body.get("resourceId")).longValue() : null;
        Long userId = currentUserId(request);
        return ApiResult.ok(pipelineEngine.createProject(name, resourceId, userId));
    }

    /** 切换可见性 */
    @PostMapping("/toggle-visibility")
    public ApiResult<?> toggleVisibility(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        Long id = body.get("id");
        Long userId = currentUserId(request);
        Project project = projectService.getById(id);
        if (project == null) return ApiResult.fail(404, "项目不存在");
        if (!project.getCreatedBy().equals(userId)) return ApiResult.fail(403, "仅创建者可操作");
        project.setIsPublic(!project.getIsPublic());
        projectService.update(project);
        return ApiResult.ok();
    }

    /** 添加剧集 */
    @PostMapping("/episode/add")
    public ApiResult<Episode> addEpisode(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        checkStagePermission(((Number) body.get("projectId")).longValue(), 2, request);
        return ApiResult.ok(pipelineEngine.addEpisode(
            ((Number) body.get("projectId")).longValue(),
            (String) body.get("title"),
            ((Number) body.get("episodeNumber")).intValue()
        ));
    }

    /** 提交剧本 */
    @PostMapping("/episode/submit-script")
    public ApiResult<?> submitScript(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long projectId = ((Number) body.get("projectId")).longValue();
        Long episodeId = ((Number) body.get("episodeId")).longValue();
        checkStagePermission(projectId, 2, request);
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId)) return ApiResult.fail(404, "剧集不存在");
        episode.setScriptContent((String) body.get("scriptContent"));
        pipelineEngine.submitScript(episode);
        return ApiResult.ok();
    }

    /** 剧本审核通过 */
    @PostMapping("/episode/approve-script")
    public ApiResult<?> approveScript(@RequestBody Map<String, Number> body, HttpServletRequest request) {
        Long projectId = body.get("projectId").longValue();
        Long episodeId = body.get("episodeId").longValue();
        checkStagePermission(projectId, 3, request);
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId)) return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.advance(episode);
        return ApiResult.ok();
    }

    /** 剧本驳回 */
    @PostMapping("/episode/reject-script")
    public ApiResult<?> rejectScript(@RequestBody Map<String, Number> body, HttpServletRequest request) {
        Long projectId = body.get("projectId").longValue();
        Long episodeId = body.get("episodeId").longValue();
        checkStagePermission(projectId, 3, request);
        pipelineEngine.reject(episodeId, EpisodeStatus.SCRIPT_DRAFT);
        return ApiResult.ok();
    }

    /** 终审通过 */
    @PostMapping("/episode/approve")
    public ApiResult<?> approve(@RequestBody Map<String, Number> body, HttpServletRequest request) {
        Long projectId = body.get("projectId").longValue();
        Long episodeId = body.get("episodeId").longValue();
        checkStagePermission(projectId, 6, request);
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId)) return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.advance(episode);
        return ApiResult.ok();
    }

    /** 终审驳回 */
    @PostMapping("/episode/reject")
    public ApiResult<?> reject(@RequestBody Map<String, Number> body, HttpServletRequest request) {
        Long projectId = body.get("projectId").longValue();
        Long episodeId = body.get("episodeId").longValue();
        checkStagePermission(projectId, 6, request);
        pipelineEngine.reject(episodeId, EpisodeStatus.GENERATING);
        return ApiResult.ok();
    }

    /** 重试失败步骤 */
    @PostMapping("/episode/retry")
    public ApiResult<?> retry(@RequestBody Map<String, Number> body, HttpServletRequest request) {
        Long projectId = body.get("projectId").longValue();
        Long episodeId = body.get("episodeId").longValue();
        Episode episode = episodeService.getById(episodeId);
        if (episode == null || !episode.getProjectId().equals(projectId)) return ApiResult.fail(404, "剧集不存在");
        checkStagePermission(projectId, episode.getStatus(), request);
        pipelineEngine.retry(episodeId);
        return ApiResult.ok();
    }

    // ====== AI 生成 ======

    /** 生成图片 */
    @PostMapping("/episode/generate-image")
    public ApiResult<?> generateImage(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long projectId = ((Number) body.get("projectId")).longValue();
        Long episodeId = ((Number) body.get("episodeId")).longValue();
        checkStagePermission(projectId, 5, request);
        GenerateRequest req = new GenerateRequest();
        req.setType(com.mlm.common.enums.ModelType.TEXT_TO_IMAGE);
        req.setVendor("stable_diffusion");
        req.setPrompt((String) body.getOrDefault("prompt", ""));
        req.setEpisodeId(episodeId);
        req.setWidth(1920);
        req.setHeight(1080);
        modelGateway.generate(req);
        return ApiResult.ok();
    }

    /** 生成视频 */
    @PostMapping("/episode/generate-video")
    public ApiResult<?> generateVideo(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long projectId = ((Number) body.get("projectId")).longValue();
        Long episodeId = ((Number) body.get("episodeId")).longValue();
        checkStagePermission(projectId, 5, request);
        GenerateRequest req = new GenerateRequest();
        req.setType(com.mlm.common.enums.ModelType.IMAGE_TO_VIDEO);
        req.setVendor("kling");
        req.setReferenceImageUrl((String) body.getOrDefault("imageUrl", "https://example.com/default.jpg"));
        req.setEpisodeId(episodeId);
        modelGateway.generate(req);
        return ApiResult.ok();
    }

    /** 完成生成 → 推进到终审 */
    @PostMapping("/episode/complete-generation")
    public ApiResult<?> completeGeneration(@RequestBody Map<String, Number> body, HttpServletRequest request) {
        Long projectId = body.get("projectId").longValue();
        Long episodeId = body.get("episodeId").longValue();
        checkStagePermission(projectId, 5, request);
        Episode episode = episodeService.getById(episodeId);
        if (episode == null) return ApiResult.fail(404, "剧集不存在");
        pipelineEngine.advance(episode);
        return ApiResult.ok();
    }

    /** 生成结果 */
    @PostMapping("/episode/results")
    public ApiResult<List<Map<String, String>>> getResults(@RequestBody Map<String, Number> body) {
        Long episodeId = body.get("episodeId").longValue();
        return ApiResult.ok(List.of(
            Map.of("url", "https://picsum.photos/seed/" + episodeId + "a/960/540", "label", "分镜1"),
            Map.of("url", "https://picsum.photos/seed/" + episodeId + "b/960/540", "label", "分镜2"),
            Map.of("url", "https://picsum.photos/seed/" + episodeId + "c/960/540", "label", "分镜3"),
            Map.of("url", "https://picsum.photos/seed/" + episodeId + "d/960/540", "label", "分镜4")
        ));
    }

    // ====== 阶段负责人 ======

    @PostMapping("/stage-members/list")
    public ApiResult<List<StageMember>> listMembers(@RequestBody Map<String, Number> body) {
        Long projectId = body.get("projectId").longValue();
        return ApiResult.ok(stageMemberMapper.selectList(
            new LambdaQueryWrapper<StageMember>().eq(StageMember::getProjectId, projectId)));
    }

    @PostMapping("/stage-members/set")
    public ApiResult<?> setMembers(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long projectId = ((Number) body.get("projectId")).longValue();
        Long userId = currentUserId(request);
        Project project = projectService.getById(projectId);
        if (project == null) return ApiResult.fail(404, "项目不存在");
        if (!project.getCreatedBy().equals(userId)) return ApiResult.fail(403, "仅创建者可设置");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) body.get("members");
        stageMemberMapper.delete(new LambdaQueryWrapper<StageMember>().eq(StageMember::getProjectId, projectId));
        for (Map<String, Object> m : members) {
            StageMember sm = new StageMember();
            sm.setProjectId(projectId);
            sm.setStage(((Number) m.get("stage")).intValue());
            sm.setUserId(((Number) m.get("userId")).longValue());
            stageMemberMapper.insert(sm);
        }
        return ApiResult.ok();
    }

    // ====== 工具 ======

    /** 通过 Authorization 头调 Gateway 获取当前用户 ID */
    private Long currentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("未登录");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:9000/api/auth/me", HttpMethod.POST, entity, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null || !Integer.valueOf(0).equals(body.get("code"))) {
                throw new RuntimeException("未登录");
            }
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return ((Number) data.get("id")).longValue();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("鉴权失败: " + e.getMessage());
        }
    }

    private void checkStagePermission(Long projectId, int stageCode, HttpServletRequest request) {
        Long userId = currentUserId(request);
        Project project = projectService.getById(projectId);
        if (project == null) throw new RuntimeException("项目不存在");
        if (project.getCreatedBy().equals(userId)) return;
        StageMember member = stageMemberMapper.selectOne(
            new LambdaQueryWrapper<StageMember>()
                .eq(StageMember::getProjectId, projectId)
                .eq(StageMember::getStage, stageCode)
                .eq(StageMember::getUserId, userId)
        );
        if (member == null) throw new RuntimeException("无权操作该阶段");
    }
}
