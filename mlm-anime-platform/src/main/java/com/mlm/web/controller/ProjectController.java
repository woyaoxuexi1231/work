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
import com.mlm.web.dto.EpisodeAddRequest;
import com.mlm.web.dto.EpisodeIdRequest;
import com.mlm.web.dto.GenerateImageRequest;
import com.mlm.web.dto.GenerateVideoRequest;
import com.mlm.web.dto.GenerationResultVO;
import com.mlm.web.dto.IdRequest;
import com.mlm.web.dto.ProjectCreateRequest;
import com.mlm.web.dto.ProjectDetailVO;
import com.mlm.web.dto.ResultsRequest;
import com.mlm.web.dto.ScriptSubmitRequest;
import com.mlm.web.dto.StageMembersListRequest;
import com.mlm.web.dto.StageMembersSetRequest;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
@Slf4j
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final EpisodeService episodeService;
    private final PipelineEngine pipelineEngine;
    private final StageMemberMapper stageMemberMapper;
    private final ModelGateway modelGateway;
    private final RestTemplate restTemplate = new RestTemplate();
    @Value("${auth.service.url:http://localhost:9000}")
    private String authServiceUrl;

    /** 项目列表 */
    @PostMapping("/list")
    public ApiResult<List<Project>> list(HttpServletRequest request) {
        Long userId = currentUserId(request);
        List<Project> all = projectService.listAll();
        all.removeIf(p -> !p.getIsPublic() && !p.getCreatedBy().equals(userId));
        return ApiResult.ok(all, "PROJECT_LIST_LOADED");
    }

    /** 项目详情（含剧集列表） */
    @PostMapping("/get")
    public ApiResult<ProjectDetailVO> get(@RequestBody IdRequest req, HttpServletRequest request) {
        if (req == null || req.getId() == null) {
            return ApiResult.fail(400, "项目ID不能为空", "INVALID_REQUEST");
        }
        Project project = projectService.getById(req.getId());
        if (project == null) return ApiResult.fail(404, "项目不存在", "PROJECT_NOT_FOUND");
        Long userId = currentUserId(request);
        if (!project.getIsPublic() && !project.getCreatedBy().equals(userId))
            return ApiResult.fail(403, "无权访问该项目", "ACCESS_DENIED");
        List<Episode> episodes = episodeService.findByProjectId(req.getId());
        return ApiResult.ok(ProjectDetailVO.builder()
                .project(project)
                .episodes(episodes)
                .build(), "PROJECT_DETAIL_LOADED");
    }

    /** 创建项目 */
    @PostMapping("/create")
    public ApiResult<Project> create(@RequestBody ProjectCreateRequest req, HttpServletRequest request) {
        if (req == null || req.getName() == null || req.getName().trim().isEmpty()) {
            return ApiResult.fail(400, "项目名称不能为空", "INVALID_REQUEST");
        }
        if (req.getName().length() > 100) {
            return ApiResult.fail(400, "项目名称不能超过100字符", "INVALID_REQUEST");
        }
        Long userId = currentUserId(request);
        Project project = pipelineEngine.createProject(req.getName().trim(), req.getResourceId(), userId);
        return ApiResult.ok(project, "PROJECT_CREATED");
    }

    /** 切换可见性 */
    @PostMapping("/toggle-visibility")
    public ApiResult<String> toggleVisibility(@RequestBody IdRequest req, HttpServletRequest request) {
        if (req == null || req.getId() == null) {
            return ApiResult.fail(400, "项目ID不能为空", "INVALID_REQUEST");
        }
        Long userId = currentUserId(request);
        Project project = projectService.getById(req.getId());
        if (project == null) return ApiResult.fail(404, "项目不存在", "PROJECT_NOT_FOUND");
        if (!project.getCreatedBy().equals(userId)) return ApiResult.fail(403, "仅创建者可操作", "PERMISSION_DENIED");
        project.setIsPublic(!project.getIsPublic());
        projectService.update(project);
        String newStatus = project.getIsPublic() ? "PROJECT_PUBLISHED" : "PROJECT_UNPUBLISHED";
        return ApiResult.ok(newStatus);
    }

    /** 添加剧集 */
    @PostMapping("/episode/add")
    public ApiResult<Episode> addEpisode(@RequestBody EpisodeAddRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null) {
            return ApiResult.fail(400, "项目ID不能为空", "INVALID_REQUEST");
        }
        checkStagePermission(req.getProjectId(), 2, request);
        Episode episode = pipelineEngine.addEpisode(
                req.getProjectId(),
                req.getTitle(),
                req.getEpisodeNumber() != null ? req.getEpisodeNumber() : 1
        );
        return ApiResult.ok(episode, "EPISODE_CREATED");
    }

    /** 提交剧本 */
    @PostMapping("/episode/submit-script")
    public ApiResult<String> submitScript(@RequestBody ScriptSubmitRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "参数不完整", "INVALID_REQUEST");
        }
        checkStagePermission(req.getProjectId(), 2, request);
        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            return ApiResult.fail(404, "剧集不存在", "EPISODE_NOT_FOUND");
        }
        episode.setScriptContent(req.getScriptContent());
        pipelineEngine.submitScript(episode);
        return ApiResult.ok("SCRIPT_SUBMITTED");
    }

    /** 剧本审核通过 */
    @PostMapping("/episode/approve-script")
    public ApiResult<String> approveScript(@RequestBody EpisodeIdRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "参数不完整", "INVALID_REQUEST");
        }
        checkStagePermission(req.getProjectId(), 3, request);
        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            return ApiResult.fail(404, "剧集不存在", "EPISODE_NOT_FOUND");
        }
        pipelineEngine.advance(episode);
        return ApiResult.ok("SCRIPT_APPROVED");
    }

    /** 剧本驳回 */
    @PostMapping("/episode/reject-script")
    public ApiResult<String> rejectScript(@RequestBody EpisodeIdRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "参数不完整", "INVALID_REQUEST");
        }
        checkStagePermission(req.getProjectId(), 3, request);
        pipelineEngine.reject(req.getEpisodeId(), EpisodeStatus.SCRIPT_DRAFT);
        return ApiResult.ok("SCRIPT_REJECTED");
    }

    /** 终审通过 */
    @PostMapping("/episode/approve")
    public ApiResult<String> approve(@RequestBody EpisodeIdRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "参数不完整", "INVALID_REQUEST");
        }
        checkStagePermission(req.getProjectId(), 6, request);
        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            return ApiResult.fail(404, "剧集不存在", "EPISODE_NOT_FOUND");
        }
        pipelineEngine.advance(episode);
        return ApiResult.ok("FINAL_APPROVED");
    }

    /** 终审驳回 */
    @PostMapping("/episode/reject")
    public ApiResult<String> reject(@RequestBody EpisodeIdRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "参数不完整", "INVALID_REQUEST");
        }
        checkStagePermission(req.getProjectId(), 6, request);
        pipelineEngine.reject(req.getEpisodeId(), EpisodeStatus.GENERATING);
        return ApiResult.ok("FINAL_REJECTED");
    }

    /** 重试失败步骤 */
    @PostMapping("/episode/retry")
    public ApiResult<String> retry(@RequestBody EpisodeIdRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "参数不完整", "INVALID_REQUEST");
        }
        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null || !episode.getProjectId().equals(req.getProjectId())) {
            return ApiResult.fail(404, "剧集不存在", "EPISODE_NOT_FOUND");
        }
        checkStagePermission(req.getProjectId(), episode.getStatus(), request);
        pipelineEngine.retry(req.getEpisodeId());
        return ApiResult.ok("EPISODE_RETRIED");
    }

    // ====== AI 生成 ======

    /** 生成图片 */
    @PostMapping("/episode/generate-image")
    public ApiResult<String> generateImage(@RequestBody GenerateImageRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "参数不完整", "INVALID_REQUEST");
        }
        checkStagePermission(req.getProjectId(), 5, request);
        GenerateRequest genReq = new GenerateRequest();
        genReq.setType(com.mlm.common.enums.ModelType.TEXT_TO_IMAGE);
        genReq.setVendor("stable_diffusion");
        genReq.setPrompt(req.getPrompt() != null ? req.getPrompt() : "");
        genReq.setEpisodeId(req.getEpisodeId());
        genReq.setWidth(1920);
        genReq.setHeight(1080);
        modelGateway.generate(genReq);
        return ApiResult.ok("IMAGE_GENERATION_STARTED");
    }

    /** 生成视频 */
    @PostMapping("/episode/generate-video")
    public ApiResult<String> generateVideo(@RequestBody GenerateVideoRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "参数不完整", "INVALID_REQUEST");
        }
        checkStagePermission(req.getProjectId(), 5, request);
        GenerateRequest genReq = new GenerateRequest();
        genReq.setType(com.mlm.common.enums.ModelType.IMAGE_TO_VIDEO);
        genReq.setVendor("kling");
        genReq.setReferenceImageUrl(req.getImageUrl() != null ? req.getImageUrl() : "https://example.com/default.jpg");
        genReq.setEpisodeId(req.getEpisodeId());
        modelGateway.generate(genReq);
        return ApiResult.ok("VIDEO_GENERATION_STARTED");
    }

    /** 完成生成 → 推进到终审 */
    @PostMapping("/episode/complete-generation")
    public ApiResult<String> completeGeneration(@RequestBody EpisodeIdRequest req, HttpServletRequest request) {
        if (req == null || req.getProjectId() == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "参数不完整", "INVALID_REQUEST");
        }
        checkStagePermission(req.getProjectId(), 5, request);
        Episode episode = episodeService.getById(req.getEpisodeId());
        if (episode == null) return ApiResult.fail(404, "剧集不存在", "EPISODE_NOT_FOUND");
        pipelineEngine.advance(episode);
        return ApiResult.ok("GENERATION_COMPLETED");
    }

    /** 生成结果 */
    @PostMapping("/episode/results")
    public ApiResult<GenerationResultVO> getResults(@RequestBody ResultsRequest req) {
        if (req == null || req.getEpisodeId() == null) {
            return ApiResult.fail(400, "剧集ID不能为空", "INVALID_REQUEST");
        }
        Long episodeId = req.getEpisodeId();
        return ApiResult.ok(GenerationResultVO.builder()
                .items(java.util.Arrays.asList(
                        GenerationResultVO.GenerationItemVO.builder().url("https://picsum.photos/seed/" + episodeId + "a/960/540").label("分镜1").build(),
                        GenerationResultVO.GenerationItemVO.builder().url("https://picsum.photos/seed/" + episodeId + "b/960/540").label("分镜2").build(),
                        GenerationResultVO.GenerationItemVO.builder().url("https://picsum.photos/seed/" + episodeId + "c/960/540").label("分镜3").build(),
                        GenerationResultVO.GenerationItemVO.builder().url("https://picsum.photos/seed/" + episodeId + "d/960/540").label("分镜4").build()
                ))
                .build());
    }

    // ====== 阶段负责人 ======

    @PostMapping("/stage-members/list")
    public ApiResult<List<StageMember>> listMembers(@RequestBody StageMembersListRequest req) {
        if (req == null || req.getProjectId() == null) {
            return ApiResult.fail(400, "项目ID不能为空", "INVALID_REQUEST");
        }
        return ApiResult.ok(stageMemberMapper.selectList(
                new LambdaQueryWrapper<StageMember>().eq(StageMember::getProjectId, req.getProjectId())));
    }

    @PostMapping("/stage-members/set")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Void> setMembers(@RequestBody StageMembersSetRequest req, HttpServletRequest httpRequest) {
        if (req == null || req.getProjectId() == null) {
            return ApiResult.fail(400, "项目ID不能为空", "INVALID_REQUEST");
        }
        Long userId = currentUserId(httpRequest);
        Project project = projectService.getById(req.getProjectId());
        if (project == null) return ApiResult.fail(404, "项目不存在", "PROJECT_NOT_FOUND");
        if (!project.getCreatedBy().equals(userId)) return ApiResult.fail(403, "仅创建者可设置", "PERMISSION_DENIED");

        // 先删除现有成员
        stageMemberMapper.delete(new LambdaQueryWrapper<StageMember>().eq(StageMember::getProjectId, req.getProjectId()));

        // 批量插入新成员
        if (req.getMembers() != null) {
            for (StageMembersSetRequest.StageMemberItem item : req.getMembers()) {
                if (item.getStage() != null && item.getUserId() != null) {
                    StageMember sm = new StageMember();
                    sm.setProjectId(req.getProjectId());
                    sm.setStage(item.getStage());
                    sm.setUserId(item.getUserId());
                    stageMemberMapper.insert(sm);
                }
            }
        }
        return ApiResult.ok();
    }

    // ====== 工具 ======

    /** 通过 Authorization 头调 Gateway 获取当前用户 ID */
    private Long currentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SecurityException("未登录");
        }
        try {
            Map<String, String> body = new HashMap<>();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    authServiceUrl + "/api/auth/me",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            Map<String, Object> respBody = resp.getBody();
            if (respBody == null || !Integer.valueOf(200).equals(respBody.get("code"))) {
                throw new SecurityException("未登录");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            if (data == null || data.get("id") == null) {
                throw new SecurityException("用户信息获取失败");
            }
            return ((Number) data.get("id")).longValue();
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("鉴权失败: " + e.getMessage());
        }
    }

    private void checkStagePermission(Long projectId, int stageCode, HttpServletRequest request) {
        Long userId = currentUserId(request);
        Project project = projectService.getById(projectId);
        if (project == null) throw new IllegalArgumentException("项目不存在");
        if (project.getCreatedBy().equals(userId)) return;
        StageMember member = stageMemberMapper.selectOne(
                new LambdaQueryWrapper<StageMember>()
                        .eq(StageMember::getProjectId, projectId)
                        .eq(StageMember::getStage, stageCode)
                        .eq(StageMember::getUserId, userId)
        );
        if (member == null) throw new IllegalArgumentException("无权操作该阶段");
    }
}
