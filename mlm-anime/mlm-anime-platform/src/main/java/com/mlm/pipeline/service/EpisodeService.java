package com.mlm.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.mapper.EpisodeMapper;
import com.mlm.pipeline.mapper.ProjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 剧集服务 — Episode 实体的 CRUD、创建和状态查询
 * <p>
 * 【职责】
 * <ul>
 *   <li>剧集实体的增删改查</li>
 *   <li>剧集创建（含项目计数更新）</li>
 *   <li>按项目/状态维度的批量查询</li>
 *   <li>项目已完成集数统计</li>
 * </ul>
 * <p>
 * 每集独立走完整的 Pipeline 管线，状态流转由
 * {@link com.mlm.pipeline.engine.PipelineEngine} 负责。
 * 此服务只提供数据库层面的操作。
 *
 * @author mlm
 * @see com.mlm.pipeline.engine.PipelineEngine
 * @see com.mlm.pipeline.engine.StateMachine
 */
@Service
public class EpisodeService {

    private static final Logger log = LoggerFactory.getLogger(EpisodeService.class);

    private final EpisodeMapper episodeMapper;
    private final ProjectMapper projectMapper;

    /**
     * 构造剧集服务
     *
     * @param episodeMapper 剧集 Mapper
     * @param projectMapper 项目 Mapper（用于更新项目计数）
     */
    public EpisodeService(EpisodeMapper episodeMapper,
                          ProjectMapper projectMapper) {
        this.episodeMapper = episodeMapper;
        this.projectMapper = projectMapper;
    }

    /**
     * 创建新剧集
     * <p>
     * 向指定项目添加新剧集，同时更新项目的总集数（episodesCount + 1）。
     *
     * @param episode 待创建的剧集实体
     * @return 创建后的剧集实体（含自增 id）
     */
    @Transactional(rollbackFor = Exception.class)
    public Episode create(Episode episode) {
        episodeMapper.insert(episode);
        log.info("剧集已创建: id={}, projectId={}, episodeNumber={}",
                episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());
        return episode;
    }

    /**
     * 添加剧集到指定项目
     * <p>
     * 创建新剧集并递增项目的总集数。
     * 校验项目必须存在，不存在时返回 null 由调用方处理。
     *
     * @param projectId      所属项目 ID
     * @param title          剧集标题
     * @param episodeNumber  集号
     * @return 创建后的剧集实体，项目不存在时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Episode addEpisode(Long projectId, String title, Integer episodeNumber) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            log.warn("项目不存在，无法添加剧集: projectId={}", projectId);
            return null;
        }

        Episode episode = new Episode();
        episode.setProjectId(projectId);
        episode.setEpisodeNumber(episodeNumber);
        episode.setTitle(title);
        episodeMapper.insert(episode);

        // 更新项目总集数
        project.setEpisodesCount(project.getEpisodesCount() + 1);
        projectMapper.updateById(project);

        log.info("剧集添加成功: id={}, projectId={}, episodeNumber={}, title={}",
                episode.getId(), projectId, episodeNumber, title);
        return episode;
    }

    /**
     * 更新项目已完成集数统计
     * <p>
     * 统计指定项目下状态为 COMPLETED 的剧集数量并回写到 project.completedCount。
     *
     * @param projectId 项目 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCompletedCount(Long projectId) {
        long completedCount = countCompleted(projectId);
        Project project = projectMapper.selectById(projectId);
        if (project != null) {
            project.setCompletedCount((int) completedCount);
            projectMapper.updateById(project);
            log.debug("项目已完成计数更新: projectId={}, completedCount={}",
                    projectId, completedCount);
        }
    }

    /**
     * 根据主键查询剧集
     *
     * @param id 剧集 ID
     * @return 剧集实体，不存在时返回 null
     */
    public Episode getById(Long id) {
        return episodeMapper.selectById(id);
    }

    /**
     * 查询某项目下的所有剧集（按集号升序）
     *
     * @param projectId 项目 ID
     * @return 剧集列表
     */
    public List<Episode> findByProjectId(Long projectId) {
        return episodeMapper.selectList(
                new LambdaQueryWrapper<Episode>()
                        .eq(Episode::getProjectId, projectId)
                        .orderByAsc(Episode::getEpisodeNumber)
        );
    }

    /**
     * 查询某项目下指定状态的剧集
     *
     * @param projectId 项目 ID
     * @param status    剧集状态
     * @return 剧集列表
     */
    public List<Episode> findByProjectAndStatus(Long projectId, EpisodeStatus status) {
        return episodeMapper.selectList(
                new LambdaQueryWrapper<Episode>()
                        .eq(Episode::getProjectId, projectId)
                        .eq(Episode::getStatus, status)
        );
    }

    /**
     * 按主状态筛选剧集
     *
     * @param status 剧集状态
     * @return 剧集列表
     */
    public List<Episode> findByStatus(EpisodeStatus status) {
        return episodeMapper.selectList(
                new LambdaQueryWrapper<Episode>()
                        .eq(Episode::getStatus, status)
        );
    }

    /**
     * 更新剧集
     *
     * @param episode 待更新的剧集实体（按 ID 更新）
     */
    public void update(Episode episode) {
        episodeMapper.updateById(episode);
    }

    /**
     * 统计某项目下状态为 COMPLETED 的剧集数
     *
     * @param projectId 项目 ID
     * @return 已完成集数
     */
    public long countCompleted(Long projectId) {
        return episodeMapper.selectCount(
                new LambdaQueryWrapper<Episode>()
                        .eq(Episode::getProjectId, projectId)
                        .eq(Episode::getStatus, EpisodeStatus.COMPLETED)
        );
    }

    /**
     * 统计某项目下的总剧集数
     *
     * @param projectId 项目 ID
     * @return 总集数
     */
    public long countByProject(Long projectId) {
        return episodeMapper.selectCount(
                new LambdaQueryWrapper<Episode>()
                        .eq(Episode::getProjectId, projectId)
        );
    }
}
