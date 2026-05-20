package com.mlm.pipeline.service;

import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.mapper.ProjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目服务 — 项目实体的 CRUD
 * <p>
 * 项目是剧集的容器，本身不走 Pipeline。
 * 状态流转在 {@link com.mlm.pipeline.entity.Episode} 中。
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectMapper projectMapper;

    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    /** 创建项目并返回（含自增 id） */
    public Project create(Project project) {
        projectMapper.insert(project);
        log.info("项目已创建: id={}, name={}", project.getId(), project.getName());
        return project;
    }

    /** 根据主键查询 */
    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    /** 查询所有项目 */
    public List<Project> listAll() {
        return projectMapper.selectList(null);
    }

    /** 更新项目信息 */
    public void update(Project project) {
        projectMapper.updateById(project);
    }
}
