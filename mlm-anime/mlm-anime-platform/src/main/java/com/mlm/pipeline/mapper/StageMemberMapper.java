package com.mlm.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mlm.pipeline.entity.StageMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 阶段成员 Mapper — 基础 CRUD 操作
 * <p>
 * 提供 {@link StageMember} 实体的增删改查。
 * 用于阶段负责人的设置和权限校验。
 *
 * @author mlm
 * @see StageMember 阶段成员实体
 * @see com.mlm.common.util.StagePermissionUtil
 * @see com.mlm.stage.controller.StageMemberController
 */
@Mapper
public interface StageMemberMapper extends BaseMapper<StageMember> {
}
