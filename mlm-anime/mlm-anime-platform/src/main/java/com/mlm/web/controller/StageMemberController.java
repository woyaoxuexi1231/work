package com.mlm.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.constant.ErrorCode;
import com.mlm.common.dto.ApiResult;
import com.mlm.common.util.AuthContext;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.entity.StageMember;
import com.mlm.pipeline.mapper.StageMemberMapper;
import com.mlm.pipeline.service.ProjectService;
import com.mlm.web.dto.StageMembersListRequest;
import com.mlm.web.dto.StageMembersSetRequest;
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
 * 阶段负责人管理控制器 — 各阶段负责人的查询和设置
 * <p>
 * 【职责】
 * <ul>
 *   <li>查询某项目的所有阶段负责人配置</li>
 *   <li>批量设置阶段负责人（先清空旧配置，再插入新配置）</li>
 * </ul>
 * <p>
 * 【权限说明】
 * <ul>
 *   <li>查询 — 任意登录用户可查看</li>
 *   <li>设置 — 仅项目创建者可操作</li>
 * </ul>
 *
 * @author mlm
 * @see StageMember 阶段成员实体
 * @see StageMembersSetRequest.StageMemberItem 阶段成员条目
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

    /**
     * 构造阶段负责人管理控制器
     *
     * @param stageMemberMapper 阶段成员 Mapper
     * @param projectService    项目服务
     * @param restTemplate      HTTP 客户端（认证网关调用）
     */
    public StageMemberController(StageMemberMapper stageMemberMapper,
                                 ProjectService projectService,
                                 RestTemplate restTemplate) {
        this.stageMemberMapper = stageMemberMapper;
        this.projectService = projectService;
        this.restTemplate = restTemplate;
    }

    /**
     * 查询项目阶段负责人列表
     *
     * @param req 请求体（项目 ID）
     * @return 阶段负责人列表
     */
    @PostMapping("/list")
    public ApiResult<List<StageMember>> listMembers(@RequestBody StageMembersListRequest req) {
        if (req == null || req.getProjectId() == null) {
            return ApiResult.fail(ErrorCode.INVALID_REQUEST, "项目ID不能为空");
        }

        List<StageMember> members = stageMemberMapper.selectList(
                new LambdaQueryWrapper<StageMember>()
                        .eq(StageMember::getProjectId, req.getProjectId()));
        return ApiResult.ok(members);
    }

    /**
     * 批量设置阶段负责人
     * <p>
     * 【执行流程】
     * <ol>
     *   <li>校验当前用户是否为项目创建者</li>
     *   <li>删除项目现有的所有阶段负责人配置</li>
     *   <li>批量插入新的阶段负责人配置</li>
     * </ol>
     * <p>
     * 这是一个全量替换操作，前端需传入完整的阶段负责人列表。
     *
     * @param req         设置请求（项目 ID + 阶段负责人列表）
     * @param httpRequest HTTP 请求（用于权限校验）
     * @return 操作成功
     */
    @PostMapping("/set")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Void> setMembers(@RequestBody StageMembersSetRequest req,
                                      HttpServletRequest httpRequest) {
        if (req == null || req.getProjectId() == null) {
            return ApiResult.fail(ErrorCode.INVALID_REQUEST, "项目ID不能为空");
        }

        Long userId = currentUserId(httpRequest);
        Project project = projectService.getById(req.getProjectId());
        if (project == null) {
            return ApiResult.fail(ErrorCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        if (!project.getCreatedBy().equals(userId)) {
            return ApiResult.fail(ErrorCode.FORBIDDEN, "仅创建者可设置");
        }

        // 先删除现有成员配置
        stageMemberMapper.delete(
                new LambdaQueryWrapper<StageMember>()
                        .eq(StageMember::getProjectId, req.getProjectId()));

        // 批量插入新成员配置
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
            log.info("阶段负责人设置完成: projectId={}, count={}",
                    req.getProjectId(), req.getMembers().size());
        }

        return ApiResult.ok();
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
