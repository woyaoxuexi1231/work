package com.mlm.project.controller;

import com.mlm.common.exception.BizException;
import com.mlm.common.result.ApiResult;
import com.mlm.common.util.AuthContext;
import com.mlm.project.dto.IdRequest;
import com.mlm.project.dto.ProjectCreateRequest;
import com.mlm.project.dto.ProjectDetailVO;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Project;
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
import java.util.List;

/**
 * 项目管理控制器 — 项目 CRUD 及可见性切换。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;
    private final EpisodeService episodeService;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://localhost:9000}")
    private String authServiceUrl;

    public ProjectController(ProjectService projectService,
                             EpisodeService episodeService,
                             RestTemplate restTemplate) {
        this.projectService = projectService;
        this.episodeService = episodeService;
        this.restTemplate = restTemplate;
    }

    /**
     * 查询项目列表 — 返回当前用户有权限查看的项目。
     */
    @PostMapping("/list")
    public ApiResult<List<Project>> list(HttpServletRequest request) {
        Long userId = currentUserId(request);
        List<Project> all = projectService.listAll();
        all.removeIf(p -> !p.getIsPublic() && !p.getCreatedBy().equals(userId));
        return ApiResult.ok(all);
    }

    /**
     * 查询项目详情（含剧集列表）。
     */
    @PostMapping("/get")
    public ApiResult<ProjectDetailVO> get(@RequestBody IdRequest req, HttpServletRequest request) {
        if (req == null || req.getId() == null) {
            throw new BizException(400, "项目ID不能为空");
        }

        Project project = projectService.getById(req.getId());
        if (project == null) {
            throw new BizException(404, "项目不存在", "PROJECT_NOT_FOUND");
        }

        Long userId = currentUserId(request);
        if (!project.getIsPublic() && !project.getCreatedBy().equals(userId)) {
            throw new BizException(403, "无权访问该项目", "FORBIDDEN");
        }

        List<Episode> episodes = episodeService.findByProjectId(req.getId());
        return ApiResult.ok(ProjectDetailVO.builder()
                .project(project)
                .episodes(episodes)
                .build());
    }

    /**
     * 创建项目 — 创建后默认为公开状态。
     */
    @PostMapping("/create")
    public ApiResult<Project> create(@RequestBody ProjectCreateRequest req, HttpServletRequest request) {
        if (req == null || req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BizException(400, "项目名称不能为空", "PROJECT_NAME_EMPTY");
        }
        if (req.getName().length() > 100) {
            throw new BizException(400, "项目名称不能超过100字符", "PROJECT_NAME_TOO_LONG");
        }

        Long userId = currentUserId(request);
        Project project = projectService.createProject(req.getName().trim(), req.getResourceId(), userId);
        return ApiResult.ok(project);
    }

    /**
     * 切换项目可见性 — 仅项目创建者可操作。
     */
    @PostMapping("/toggle-visibility")
    public ApiResult<String> toggleVisibility(@RequestBody IdRequest req, HttpServletRequest request) {
        if (req == null || req.getId() == null) {
            throw new BizException(400, "项目ID不能为空");
        }

        Long userId = currentUserId(request);
        Project project = projectService.getById(req.getId());
        if (project == null) {
            throw new BizException(404, "项目不存在", "PROJECT_NOT_FOUND");
        }
        if (!project.getCreatedBy().equals(userId)) {
            throw new BizException(403, "仅创建者可操作", "FORBIDDEN");
        }

        project.setIsPublic(!project.getIsPublic());
        projectService.update(project);

        String newStatus = project.getIsPublic() ? "PROJECT_PUBLISHED" : "PROJECT_UNPUBLISHED";
        return ApiResult.ok(newStatus);
    }

    private Long currentUserId(HttpServletRequest request) {
        return AuthContext.currentUserId(request, restTemplate, authServiceUrl);
    }
}
