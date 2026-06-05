package com.mlm.stage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.exception.BizException;
import com.mlm.common.result.ApiResult;
import com.mlm.common.util.AuthContext;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.entity.StageMember;
import com.mlm.pipeline.mapper.StageMemberMapper;
import com.mlm.pipeline.service.ProjectService;
import com.mlm.stage.dto.StageMembersListRequest;
import com.mlm.stage.dto.StageMembersSetRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 阶段负责人管理控制器 — 各阶段负责人的查询和设置。
 */
@RestController
@RequestMapping("/api/projects/stage-members")
public class StageMemberController {

    private static final Logger log = LoggerFactory.getLogger(StageMemberController.class);

    private final StageMemberMapper stageMemberMapper;
    private final ProjectService projectService;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://localhost:9000}")
    private String authServiceUrl;

    public StageMemberController(StageMemberMapper stageMemberMapper,
                                 ProjectService projectService,
                                 RestTemplate restTemplate) {
        this.stageMemberMapper = stageMemberMapper;
        this.projectService = projectService;
        this.restTemplate = restTemplate;
    }

    /**
     * 查询项目阶段负责人列表。
     */
    @PostMapping("/list")
    public ApiResult<List<StageMember>> listMembers(@RequestBody StageMembersListRequest req) {
        if (req == null || req.getProjectId() == null) {
            throw new BizException(400, "项目ID不能为空");
        }

        List<StageMember> members = stageMemberMapper.selectList(
                new LambdaQueryWrapper<StageMember>()
                        .eq(StageMember::getProjectId, req.getProjectId()));
        return ApiResult.ok(members);
    }

    /**
     * 批量设置阶段负责人 — 全量替换操作。
     */
    @PostMapping("/set")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Void> setMembers(@RequestBody StageMembersSetRequest req,
                                      HttpServletRequest httpRequest) {
        if (req == null || req.getProjectId() == null) {
            throw new BizException(400, "项目ID不能为空");
        }

        Long userId = currentUserId(httpRequest);
        Project project = projectService.getById(req.getProjectId());
        if (project == null) {
            throw new BizException(404, "项目不存在", "PROJECT_NOT_FOUND");
        }
        if (!project.getCreatedBy().equals(userId)) {
            throw new BizException(403, "仅创建者可设置", "FORBIDDEN");
        }

        // 先删除现有成员配置
        stageMemberMapper.delete(
                new LambdaQueryWrapper<StageMember>()
                        .eq(StageMember::getProjectId, req.getProjectId()));

        // 批量插入新成员
        if (req.getMembers() != null && !req.getMembers().isEmpty()) {
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

    private Long currentUserId(HttpServletRequest request) {
        return AuthContext.currentUserId(request, restTemplate, authServiceUrl);
    }
}
