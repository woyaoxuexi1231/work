package com.mlm.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.enums.EpisodeStatus;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.mapper.EpisodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 剧集服务 — Episode 实体的 CRUD + 状态查询
 * <p>
 * 每集独立走完整的 Pipeline 管线，此服务提供数据库层面的操作。
 * 状态流转由 {@link com.mlm.pipeline.engine.PipelineEngine} 负责。
 */
@Service
public class EpisodeService {

    private static final Logger log = LoggerFactory.getLogger(EpisodeService.class);

    private final EpisodeMapper episodeMapper;

    public EpisodeService(EpisodeMapper episodeMapper) {
        this.episodeMapper = episodeMapper;
    }

    /** 创建新剧集 */
    public Episode create(Episode episode) {
        episodeMapper.insert(episode);
        log.info("剧集已创建: id={}, projectId={}, episode={}", episode.getId(), episode.getProjectId(), episode.getEpisodeNumber());
        return episode;
    }

    /** 根据主键查询 */
    public Episode getById(Long id) {
        return episodeMapper.selectById(id);
    }

    /** 查询某项目下的所有剧集 */
    public List<Episode> findByProjectId(Long projectId) {
        return episodeMapper.selectList(
            new LambdaQueryWrapper<Episode>()
                .eq(Episode::getProjectId, projectId)
                .orderByAsc(Episode::getEpisodeNumber)
        );
    }

    /** 查询某项目下指定状态的剧集 */
    public List<Episode> findByProjectAndStatus(Long projectId, EpisodeStatus status) {
        return episodeMapper.selectList(
            new LambdaQueryWrapper<Episode>()
                .eq(Episode::getProjectId, projectId)
                .eq(Episode::getStatus, status)
        );
    }

    /** 按主状态筛选 */
    public List<Episode> findByStatus(EpisodeStatus status) {
        return episodeMapper.selectList(
            new LambdaQueryWrapper<Episode>().eq(Episode::getStatus, status)
        );
    }

    /** 更新剧集 */
    public void update(Episode episode) {
        episodeMapper.updateById(episode);
    }

    /** 统计某项目已完成集数 */
    public long countCompleted(Long projectId) {
        return episodeMapper.selectCount(
            new LambdaQueryWrapper<Episode>()
                .eq(Episode::getProjectId, projectId)
                .eq(Episode::getStatus, EpisodeStatus.COMPLETED)
        );
    }

    /** 统计某项目总集数 */
    public long countByProject(Long projectId) {
        return episodeMapper.selectCount(
            new LambdaQueryWrapper<Episode>()
                .eq(Episode::getProjectId, projectId)
        );
    }
}
