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
 * Pipeline 核心引擎 — 剧集状态机的同步驱动
 * <p>
 * <b>设计模式：模板方法 + 策略 + 状态</b>
 * <ol>
 *   <li><b>模板方法 (Template Method)</b> — {@link #advance(Episode)} 定义了不可变的推进骨架：
 *       计算目标状态 → 乐观锁更新 → 委派 Handler → 收尾处理，子类不可覆盖</li>
 *   <li><b>策略 (Strategy)</b> — {@link StepHandler} 接口族，每个 {@link EpisodeStatus}
 *       对应一个 Handler 实现，新增步骤只需加实现类</li>
 *   <li><b>状态 (State)</b> — {@link StateMachine} 集中管理合法流转路径，避免 if-else 散落各处</li>
 * </ol>
 *
 * <b>为什么不需要消息队列？</b>
 * 状态机是<em>确定性的同步计算</em>：下一个状态完全由当前状态决定，没有异步等待的必要。
 * 给确定性状态流转加 MQ 只增加了延迟、序列化开销和调试复杂度（属于架构过度设计）。
 * 真实的异步边界在 AI 模型调用处（由 {@link com.mlm.model.core.ModelGateway} 和
 * {@link com.mlm.pipeline.scheduler.TaskPollingScheduler} 处理），那里才是 MQ 的合理位置。
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

    // ==================== 公开 API ====================

    /**
     * 【模板方法】推进剧集到状态机的下一步
     * <p>
     * 骨架不可变，每一步的执行细节由对应的 {@link StepHandler} 策略决定。
     *
     * @param episode 当前剧集（必须含 id 和 status）
     */
    @Transactional
    public void advance(Episode episode) {
        Objects.requireNonNull(episode.getId(), "episode.id must not be null");
        Objects.requireNonNull(episode.getStatus(), "episode.status must not be null");

        EpisodeStatus current = episode.getStatus();
        EpisodeStatus next = StateMachine.next(current);
        log.info("推进剧集: episodeId={}, projectId={}, {} → {}",
            episode.getId(), episode.getProjectId(), current.getLabel(), next.getLabel());

        // 1. 乐观锁 CAS —— 防止并发重复推进（WHERE status = ?）
        int updated = episodeMapper.updateStatus(
            episode.getId(), current.name(), next.name(), StepStatus.PROCESSING.name()
        );
        if (updated == 0) {
            log.warn("状态更新失败（可能并发）, episodeId={}, from={}, to={}", episode.getId(), current, next);
            return;
        }
        episode.setStatus(next);
        episode.setStepStatus(StepStatus.PROCESSING);

        // 2. 委派策略执行
        StepHandler handler = registry.get(next);
        try {
            handler.handle(episode);
            log.info("步骤执行完成: episodeId={}, step={}", episode.getId(), next);
        } catch (Exception e) {
            log.error("步骤执行失败: episodeId={}, step={}", episode.getId(), next, e);
            episodeMapper.markStepFailed(episode.getId(), e.getMessage());
        }

        // 3. 收尾：剧集完成时更新项目计数
        if (next == EpisodeStatus.COMPLETED) {
            completeEpisode(episode);
        }
    }

    /**
     * 提交剧本 → 保存内容 → 推进到审核
     * <p>
     * 等价于 save + advance，封装为单一事务确保一致性。
     */
    @Transactional
    public void submitScript(Episode episode) {
        episode.setStatus(EpisodeStatus.SCRIPT_DRAFT);
        episode.setStepStatus(StepStatus.PENDING);
        episodeMapper.updateById(episode);
        advance(episode);
        log.info("提交剧本: episodeId={}, projectId={}", episode.getId(), episode.getProjectId());
    }

    /**
     * 手动重试失败步骤
     */
    @Transactional
    public void retry(Long episodeId) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) throw new PipelineException("剧集不存在: " + episodeId);
        if (episode.getStepStatus() != StepStatus.FAILED) {
            throw new PipelineException("当前步骤未失败，无需重试");
        }

        log.info("重试: episodeId={}, step={}, error={}", episodeId, episode.getStatus(), episode.getErrorMsg());
        episode.setStepStatus(StepStatus.PENDING);
        episodeMapper.updateById(episode);
        advance(episode);
    }

    /**
     * 驳回剧集到指定状态（仅允许 StateMachine 定义的合法路径）
     */
    @Transactional
    public void reject(Long episodeId, EpisodeStatus targetStatus) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) throw new PipelineException("剧集不存在: " + episodeId);
        if (!StateMachine.canTransition(episode.getStatus(), targetStatus)) {
            throw new PipelineException("不允许从 " + episode.getStatus() + " 驳回到 " + targetStatus);
        }

        log.info("驳回: episodeId={}, from={}, to={}", episodeId, episode.getStatus(), targetStatus);
        episodeMapper.updateStatus(episodeId, episode.getStatus().name(), targetStatus.name(), StepStatus.PENDING.name());
    }

    /**
     * 创建项目（纯容器，不创建剧集）
     * <p>
     * 所有剧集（包括第一集）由用户手动通过 {@link #addEpisode} 添加。
     */
    @Transactional
    public Project createProject(String name, Long resourceId) {
        Project project = new Project();
        project.setName(name);
        project.setResourceId(resourceId);
        project.setEpisodesCount(0);
        project.setCompletedCount(0);
        projectMapper.insert(project);
        log.info("项目创建: id={}, name={}, resourceId={}", project.getId(), name, resourceId);
        return project;
    }

    /**
     * 为已有项目添加新剧集
     */
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

        log.info("添加剧集: projectId={}, episodeId={}, #{}({})", projectId, episode.getId(), episodeNumber, title);
        return episode;
    }

    // ==================== 内部方法 ====================

    /** 剧集完成时更新项目的已完成计数 */
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
            log.info("剧集完成: projectId={}, episodeId={}, 已完成 {}/{} 集",
                project.getId(), episode.getId(), completed, project.getEpisodesCount());
        }
    }
}
