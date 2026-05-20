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
 * Pipeline 核心引擎
 * <p>
 * 【设计模式】模板方法 + 策略 + 状态
 * <p>
 * 【状态编码约定】
 * Entity 中 status/stepStatus 存的是 int 码（数据库也是 int），
 * 业务逻辑在边界处用 EpisodeStatus.of(int) / StepStatus.of(int) 转为枚举，
 * 枚举用于类型安全的比较和标签获取，int 用于存储和传输。
 */
@Component
public class PipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineEngine.class);

    /** 终态集合 — 到达后不需要 Handler 执行 */
    private static final EpisodeStatus[] TERMINAL_STATES = {
        EpisodeStatus.COMPLETED
    };

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

    // ==================== 公开方法 ====================

    /**
     * 推进剧集到下一步
     * <p>
     * 骨架：{@code int → enum → StateMachine → CAS → enum→int → Handler}
     */
    @Transactional
    public void advance(Episode episode) {
        Objects.requireNonNull(episode.getId(), "episode.id 不能为空");
        Objects.requireNonNull(episode.getStatus(), "episode.status 不能为空");

        // 边界：int → enum
        EpisodeStatus current = EpisodeStatus.of(episode.getStatus());
        EpisodeStatus next = StateMachine.next(current);
        log.info(">>> 推进剧集: episodeId={}, {}→{} ({}→{})",
            episode.getId(), current.getCode(), next.getCode(), current.getLabel(), next.getLabel());

        // CAS 乐观锁（数据库操作直接用 int 码）
        int updated = episodeMapper.updateStatus(
            episode.getId(), current.getCode(), next.getCode(), StepStatus.PROCESSING.getCode()
        );
        if (updated == 0) {
            log.warn("状态更新失败（并发）, episodeId={}", episode.getId());
            return;
        }
        // Entity 回写 int 值
        episode.setStatus(next.getCode());
        episode.setStepStatus(StepStatus.PROCESSING.getCode());

        // 终态提前返回，不需要 Handler
        if (isTerminal(next)) {
            log.info("到达终态: episodeId={}, status={}", episode.getId(), next.getLabel());
            if (next == EpisodeStatus.COMPLETED) {
                completeEpisode(episode);
            }
            return;
        }

        // 非终态 → 委派 Handler
        StepHandler handler = registry.get(next);
        try {
            handler.handle(episode);
            log.info("步骤成功: episodeId={}, step={}", episode.getId(), next.getLabel());
        } catch (Exception e) {
            log.error("步骤失败: episodeId={}, step={}", episode.getId(), next.getLabel(), e);
            episodeMapper.markStepFailed(episode.getId(), e.getMessage());
        }
    }

    /** 提交剧本 → 推进到 SCRIPT_REVIEW */
    @Transactional
    public void submitScript(Episode episode) {
        episode.setStatus(EpisodeStatus.SCRIPT_DRAFT.getCode());
        episode.setStepStatus(StepStatus.PENDING.getCode());
        episodeMapper.updateById(episode);
        advance(episode);
    }

    /** 手动重试失败步骤 */
    @Transactional
    public void retry(Long episodeId) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) throw new PipelineException("剧集不存在: " + episodeId);
        // int → enum 比较
        if (episode.getStepStatus() != StepStatus.FAILED.getCode()) {
            throw new PipelineException("当前步骤未失败，无需重试");
        }
        log.info("重试: episodeId={}, status={}", episodeId, episode.getStatus());
        episode.setStepStatus(StepStatus.PENDING.getCode());
        episodeMapper.updateById(episode);
        advance(episode);
    }

    /** 驳回剧集 */
    @Transactional
    public void reject(Long episodeId, EpisodeStatus target) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) throw new PipelineException("剧集不存在: " + episodeId);
        // 转换 int → enum 进行状态机校验
        EpisodeStatus current = EpisodeStatus.of(episode.getStatus());
        if (!StateMachine.canTransition(current, target)) {
            throw new PipelineException("不允许驳回: " + current.getLabel() + " → " + target.getLabel());
        }
        log.info("驳回: episodeId={}, {}→{}", episodeId, current.getLabel(), target.getLabel());
        episodeMapper.updateStatus(episodeId, current.getCode(), target.getCode(), StepStatus.PENDING.getCode());
    }

    /** 创建项目 */
    @Transactional
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

    /** 添加剧集 */
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

    // ==================== 内部方法 ====================

    private boolean isTerminal(EpisodeStatus status) {
        for (EpisodeStatus ts : TERMINAL_STATES) {
            if (ts == status) return true;
        }
        return false;
    }

    private void completeEpisode(Episode episode) {
        episode.setStepStatus(StepStatus.SUCCESS.getCode());
        episodeMapper.updateById(episode);

        Project project = projectMapper.selectById(episode.getProjectId());
        if (project != null) {
            long completed = episodeMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Episode>()
                    .eq(Episode::getProjectId, project.getId())
                    .eq(Episode::getStatus, EpisodeStatus.COMPLETED.getCode())
            );
            project.setCompletedCount((int) completed);
            projectMapper.updateById(project);
        }
    }
}
