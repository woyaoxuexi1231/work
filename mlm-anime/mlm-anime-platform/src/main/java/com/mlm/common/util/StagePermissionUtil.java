package com.mlm.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.constant.ErrorCode;
import com.mlm.common.exception.BizException;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.entity.StageMember;
import com.mlm.pipeline.mapper.StageMemberMapper;
import com.mlm.pipeline.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阶段权限校验工具 — 校验当前用户是否有权操作指定阶段
 * <p>
 * 【校验规则】
 * <ol>
 *   <li>项目创建者 — 拥有所有阶段的权限</li>
 *   <li>阶段负责人 — 通过 {@link StageMemberMapper} 查询用户是否被设置为该阶段的负责人</li>
 *   <li>其他用户 — 无权操作</li>
 * </ol>
 *
 * @author mlm
 */
public final class StagePermissionUtil {

    private static final Logger log = LoggerFactory.getLogger(StagePermissionUtil.class);

    private StagePermissionUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 校验当前用户是否有权操作指定阶段
     * <p>
     * 先检查用户是否为项目创建者（创建者拥有所有阶段权限），
     * 再检查用户是否被设置为该阶段的负责人。
     *
     * @param projectId         项目 ID
     * @param stageCode         阶段编码（EpisodeStatus 的 int code）
     * @param userId            当前用户 ID
     * @param projectService    项目服务
     * @param stageMemberMapper 阶段成员 Mapper
     * @throws BizException 项目不存在或无权限时抛出
     */
    public static void checkStagePermission(Long projectId,
                                            int stageCode,
                                            Long userId,
                                            ProjectService projectService,
                                            StageMemberMapper stageMemberMapper) {
        Project project = projectService.getById(projectId);
        if (project == null) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在");
        }

        // 创建者拥有所有阶段权限
        if (project.getCreatedBy().equals(userId)) {
            return;
        }

        // 检查是否为该阶段的负责人
        StageMember member = stageMemberMapper.selectOne(
                new LambdaQueryWrapper<StageMember>()
                        .eq(StageMember::getProjectId, projectId)
                        .eq(StageMember::getStage, stageCode)
                        .eq(StageMember::getUserId, userId)
        );

        if (member == null) {
            log.warn("阶段权限不足: userId={}, projectId={}, stageCode={}", userId, projectId, stageCode);
            throw new BizException(ErrorCode.STAGE_PERMISSION_DENIED, "无权操作该阶段");
        }
    }
}
