package com.mlm.web.controller;

import com.mlm.common.constant.ErrorCode;
import com.mlm.common.dto.ApiResult;
import com.mlm.common.util.AuthContext;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.service.ProjectService;
import com.mlm.web.dto.IdRequest;
import com.mlm.web.dto.ProjectCreateRequest;
import com.mlm.web.dto.ProjectDetailVO;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.service.EpisodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * 项目管理控制器 — 项目 CRUD 及可见性切换
 * <p>
 * 提供项目的创建、查询列表、查询详情和可见性切换功能。
 * 每个项目可包含多个剧集，详见 {@link Episode}。
 * <p>
 * 【权限说明】
 * <ul>
 *   <li>公开项目（isPublic=true）— 所有人可查看</li>
 *   <li>私有项目（isPublic=false）— 仅创建者可见</li>
 *   <li>项目创建 — 记录创建者用户 ID</li>
 *   <li>可见性切换 — 仅创建者可操作</li>
 * </ul>
 *
 * @author mlm
 * @see EpisodeController 剧集操作
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

    /**
     * 构造项目管理控制器
     *
     * @param projectService 项目服务
     * @param episodeService 剧集服务
     * @param restTemplate   HTTP 客户端（认证网关调用）
     */
    public ProjectController(ProjectService projectService,
                             EpisodeService episodeService,
                             RestTemplate restTemplate) {
        this.projectService = projectService;
        this.episodeService = episodeService;
        this.restTemplate = restTemplate;
    }

    /**
     * 查询项目列表
     * <p>
     * 返回当前用户有权限查看的所有项目（公开项目 + 当前用户创建的私有项目）。
     *
     * @param request HTTP 请求（用于提取当前用户）
     * @return 项目列表
     */
    @PostMapping("/list")
    public ApiResult<List<Project>> list(HttpServletRequest request) {
        Long userId = currentUserId(request);
        List<Project> all = projectService.listAll();
        all.removeIf(p -> !p.getIsPublic() && !p.getCreatedBy().equals(userId));
        log.debug("项目列表查询: userId={}, count={}", userId, all.size());
        return ApiResult.ok(all);
    }

    /**
     * 查询项目详情（含剧集列表）
     *
     * @param req     请求体（项目 ID）
     * @param request HTTP 请求（用于权限校验）
     * @return 项目详情（含剧集列表）
     */
    @PostMapping("/get")
    public ApiResult<ProjectDetailVO> get(@RequestBody IdRequest req, HttpServletRequest request) {
        if (req == null || req.getId() == null) {
            return ApiResult.fail(ErrorCode.INVALID_REQUEST, "项目ID不能为空");
        }

        Project project = projectService.getById(req.getId());
        if (project == null) {
            return ApiResult.fail(ErrorCode.PROJECT_NOT_FOUND, "项目不存在");
        }

        // 权限校验：私有项目仅创建者可查看
        Long userId = currentUserId(request);
        if (!project.getIsPublic() && !project.getCreatedBy().equals(userId)) {
            return ApiResult.fail(ErrorCode.FORBIDDEN, "无权访问该项目");
        }

        List<Episode> episodes = episodeService.findByProjectId(req.getId());
        log.debug("项目详情查询: projectId={}, episodesCount={}", req.getId(), episodes.size());

        return ApiResult.ok(ProjectDetailVO.builder()
                .project(project)
                .episodes(episodes)
                .build());
    }

    /**
     * 创建项目
     * <p>
     * 创建后项目默认为公开状态，创建者自动获得管理权限。
     *
     * @param req     创建请求（项目名称、可选的资源引用 ID）
     * @param request HTTP 请求（用于提取当前用户）
     * @return 创建后的项目实体（含自增 ID）
     */
    @PostMapping("/create")
    public ApiResult<Project> create(@RequestBody ProjectCreateRequest req, HttpServletRequest request) {
        if (req == null || req.getName() == null || req.getName().trim().isEmpty()) {
            return ApiResult.fail(ErrorCode.PROJECT_NAME_EMPTY, "项目名称不能为空");
        }
        if (req.getName().length() > 100) {
            return ApiResult.fail(ErrorCode.PROJECT_NAME_TOO_LONG, "项目名称不能超过100字符");
        }

        Long userId = currentUserId(request);
        Project project = projectService.createProject(req.getName().trim(), req.getResourceId(), userId);
        log.info("项目创建成功: id={}, name={}, createdBy={}", project.getId(), project.getName(), userId);
        return ApiResult.ok(project);
    }

    /**
     * 切换项目可见性
     * <p>
     * 仅项目创建者可操作。公开 ↔ 私有 互切。
     *
     * @param req     请求体（项目 ID）
     * @param request HTTP 请求（用于权限校验）
     * @return 新的可见性状态描述
     */
    @PostMapping("/toggle-visibility")
    public ApiResult<String> toggleVisibility(@RequestBody IdRequest req, HttpServletRequest request) {
        if (req == null || req.getId() == null) {
            return ApiResult.fail(ErrorCode.INVALID_REQUEST, "项目ID不能为空");
        }

        Long userId = currentUserId(request);
        Project project = projectService.getById(req.getId());
        if (project == null) {
            return ApiResult.fail(ErrorCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        if (!project.getCreatedBy().equals(userId)) {
            return ApiResult.fail(ErrorCode.FORBIDDEN, "仅创建者可操作");
        }

        project.setIsPublic(!project.getIsPublic());
        projectService.update(project);

        String newStatus = project.getIsPublic() ? "PROJECT_PUBLISHED" : "PROJECT_UNPUBLISHED";
        log.info("项目可见性切换: id={}, newStatus={}, operator={}", req.getId(), newStatus, userId);
        return ApiResult.ok(newStatus);
    }

    /**
     * 从 HTTP 请求中提取当前用户 ID
     *
     * @param request HTTP 请求
     * @return 当前登录用户 ID
     */
    private Long currentUserId(HttpServletRequest request) {
        return AuthContext.currentUserId(request, restTemplate, authServiceUrl);
    }
}
