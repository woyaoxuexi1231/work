package com.mlm.pipeline.engine;

import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.StepStatus;
import com.mlm.common.exception.PipelineException;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.entity.Project;
import com.mlm.pipeline.mapper.EpisodeMapper;
import com.mlm.pipeline.mapper.ProjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Pipeline 核心引擎 — 剧集状态机的同步驱动（int 编码状态）
 * <p>
 * <b>设计模式：模板方法 + 策略 + 状态</b>
 */
@Component
public class PipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineEngine.class);

    private final EpisodeMapper episodeMapper;
    private final ProjectMapper projectMapper;
    private final StepHandlerRegistry registry;

    public PipelineEngine(EpisodeMapper episodeMapper,
                          ProjectMapper projectMapper,
                          StepHandlerRegistry registry) {
        this.episodeMapper = episodeMapper;
        this.projectMapper = projectMapper;
        this.registry = registry;
    }

    @Transactional
    public void advance(Episode episode) {
        Objects.requireNonNull(episode.getId());
        Objects.requireNonNull(episode.getStatus());

        EpisodeStatus current = episode.getStatus();
        EpisodeStatus next = StateMachine.next(current);
        log.info("推进剧集: episodeId={}, {}→{} ({}→{})",
            episode.getId(), current.getCode(), next.getCode(), current.getLabel(), next.getLabel());

        int updated = episodeMapper.updateStatus(
            episode.getId(), current.getCode(), next.getCode(), StepStatus.PROCESSING.getCode()
        );
        if (updated == 0) {
            log.warn("状态更新失败（并发）, episodeId={}", episode.getId());
            return;
        }
        episode.setStatus(next);
        episode.setStepStatus(StepStatus.PROCESSING);

        StepHandler handler = registry.get(next);
        try {
            handler.handle(episode);
            log.info("步骤完成: episodeId={}, step={}", episode.getId(), next.getLabel());
        } catch (Exception e) {
            log.error("步骤失败: episodeId={}, step={}", episode.getId(), next.getLabel(), e);
            episodeMapper.markStepFailed(episode.getId(), e.getMessage());
        }

        if (next == EpisodeStatus.COMPLETED) {
            completeEpisode(episode);
        }
    }

    @Transactional
    public void submitScript(Episode episode) {
        episode.setStatus(EpisodeStatus.SCRIPT_DRAFT);
        episode.setStepStatus(StepStatus.PENDING);
        episodeMapper.updateById(episode);
        advance(episode);
    }

    @Transactional
    public void retry(Long episodeId) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) throw new PipelineException("剧集不存在: " + episodeId);
        if (episode.getStepStatus() != StepStatus.FAILED)
            throw new PipelineException("当前步骤未失败");

        episode.setStepStatus(StepStatus.PENDING);
        episodeMapper.updateById(episode);
        advance(episode);
    }

    @Transactional
    public void reject(Long episodeId, EpisodeStatus target) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) throw new PipelineException("剧集不存在: " + episodeId);
        if (!StateMachine.canTransition(episode.getStatus(), target))
            throw new PipelineException("不允许驳回: " + episode.getStatus() + " → " + target);

        log.info("驳回: episodeId={}, {}→{}", episodeId, episode.getStatus().getCode(), target.getCode());
        episodeMapper.updateStatus(episodeId, episode.getStatus().getCode(), target.getCode(), StepStatus.PENDING.getCode());
    }

    @Transactional
    public Project createProject(String name, Long resourceId, Long createdBy) {
        Project project = new Project();
        project.setName(name);
        project.setResourceId(resourceId);
        project.setCreatedBy(createdBy);
        project.setIsPublic(true);
        projectMapper.insert(project);
        return project;
    }

    @Transactional
    public Episode addEpisode(Long projectId, String title, Integer episodeNumber) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new PipelineException("项目不存在: " + projectId);

        Episode episode = new Episode();
        episode.setProjectId(projectId);
        episode.setEpisodeNumber(episodeNumber);
        episode.setTitle(title);
        episodeMapper.insert(episode);

        project.setEpisodesCount(project.getEpisodesCount() + 1);
        projectMapper.updateById(project);

        return episode;
    }

    private void completeEpisode(Episode episode) {
        episode.setStepStatus(StepStatus.SUCCESS);
        episodeMapper.updateById(episode);

        Project project = projectMapper.selectById(episode.getProjectId());
        if (project != null) {
            long completed = episodeMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Episode>()
                    .eq(Episode::getProjectId, project.getId())
                    .eq(Episode::getStatus, EpisodeStatus.COMPLETED)
            );
            project.setCompletedCount((int) completed);
            projectMapper.updateById(project);
        }
    }
}
