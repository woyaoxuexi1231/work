package com.mlm.pipeline.service;

import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.mapper.ProjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目服务 — 项目实体的 CRUD 及业务管理
 * <p>
 * 【职责】
 * <ul>
 *   <li>项目实体的增删改查</li>
 *   <li>项目创建（含默认值设置）</li>
 *   <li>已完成集数统计（关联 {@link Episode}）</li>
 * </ul>
 * <p>
 * 项目是剧集的容器，本⾝不走 Pipeline 状态机。
 * Pipeline 流转在 {@link Episode} 层级。
 *
 * @author mlm
 * @see EpisodeService 剧集服务
 * @see com.mlm.pipeline.engine.PipelineEngine 状态机引擎
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectMapper projectMapper;

    /**
     * 构造项目服务
     *
     * @param projectMapper 项目 Mapper
     */
    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    /**
     * 创建项目
     * <p>
     * 创建后项目默认为公开状态，isPublic=true。
     *
     * @param name       项目名称
     * @param resourceId 可选引用资源 ID
     * @param createdBy  创建者用户 ID
     * @return 创建后的项目实体（含自增 id）
     */
    @Transactional(rollbackFor = Exception.class)
    public Project createProject(String name, Long resourceId, Long createdBy) {
        Project project = new Project();
        project.setName(name);
        project.setResourceId(resourceId);
        project.setCreatedBy(createdBy);
        project.setIsPublic(true);
        projectMapper.insert(project);
        log.info("项目创建: id={}, name={}, createdBy={}", project.getId(), name, createdBy);
        return project;
    }

    /**
     * 根据主键查询项目
     *
     * @param id 项目 ID
     * @return 项目实体，不存在时返回 null
     */
    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    /**
     * 查询所有项目
     *
     * @return 全部项目列表
     */
    public List<Project> listAll() {
        return projectMapper.selectList(null);
    }

    /**
     * 更新项目信息
     *
     * @param project 待更新的项目实体（按 ID 更新）
     */
    public void update(Project project) {
        projectMapper.updateById(project);
    }
}
