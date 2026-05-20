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
 * 【设计模式】
 * <ul>
 *   <li><b>模板方法 (Template Method)</b> — advance() 定义不可变的推进骨架：
 *       计算目标状态 → 乐观锁 CAS → 委派 Handler → 收尾处理</li>
 *   <li><b>策略 (Strategy)</b> — StepHandler 接口族，每个 EpisodeStatus 对应一个实现</li>
 *   <li><b>状态 (State)</b> — StateMachine 集中管理合法流转路径</li>
 * </ul>
 * <p>
 * 【重要说明】
 * COMPLETED 是终态，不需要 Handler 执行任何业务逻辑，
 * advance() 遇到 COMPLETED 会跳过 handler 直接调用 completeEpisode()。
 */
@Component
public class PipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineEngine.class);

    /** 终态集合 — 到达这些状态后不需要执行 Handler，直接完成 */
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

    /**
     * 【模板方法】推进剧集到下一步
     * <p>
     * 骨架不可变：
     * <ol>
     *   <li>StateMachine 计算 next 状态</li>
     *   <li>乐观锁 CAS 更新数据库（WHERE status = expectedStatus）防并发</li>
     *   <li>终态 → 直接完成；非终态 → 委派 StepHandler 策略执行</li>
     *   <li>如果终态是 COMPLETED，更新项目的 completedCount</li>
     * </ol>
     *
     * @param episode 当前剧集（必须含 id 和 status）
     */
    @Transactional
    public void advance(Episode episode) {
        Objects.requireNonNull(episode.getId(), "episode.id 不能为空");
        Objects.requireNonNull(episode.getStatus(), "episode.status 不能为空");

        EpisodeStatus current = episode.getStatus();
        EpisodeStatus next = StateMachine.next(current);
        log.info(">>> 推进剧集: episodeId={}, {}→{} ({}→{})",
            episode.getId(), current.getCode(), next.getCode(),
            current.getLabel(), next.getLabel());

        // ===== 1. 乐观锁 CAS 更新 =====
        // 条件 WHERE id=? AND status=current 保证并发安全
        int updated = episodeMapper.updateStatus(
            episode.getId(), current.getCode(), next.getCode(), StepStatus.PROCESSING.getCode()
        );
        if (updated == 0) {
            log.warn("状态更新失败（可能并发）, episodeId={}, 当前状态={}", episode.getId(), current.getCode());
            return;
        }
        episode.setStatus(next);
        episode.setStepStatus(StepStatus.PROCESSING);

        // ===== 2. 执行业务逻辑 =====
        // 终态不需要 Handler（如 COMPLETED），直接完成
        if (isTerminal(next)) {
            log.info("终态到达: episodeId={}, status={}", episode.getId(), next.getLabel());
            if (next == EpisodeStatus.COMPLETED) {
                completeEpisode(episode);
            }
            return;
        }

        // 非终态 → 委派对应 Handler
        StepHandler handler = registry.get(next);
        try {
            handler.handle(episode);
            log.info("步骤执行成功: episodeId={}, step={}", episode.getId(), next.getLabel());
        } catch (Exception e) {
            log.error("步骤执行失败: episodeId={}, step={}", episode.getId(), next.getLabel(), e);
            episodeMapper.markStepFailed(episode.getId(), e.getMessage());
        }
    }

    /**
     * 提交剧本 → 保存剧本内容 → 推进到 SCRIPT_REVIEW（等待审核）
     * <p>
     * 调用方确保 episode 已包含 scriptContent 和正确的项目关联。
     */
    @Transactional
    public void submitScript(Episode episode) {
        episode.setStatus(EpisodeStatus.SCRIPT_DRAFT);
        episode.setStepStatus(StepStatus.PENDING);
        episodeMapper.updateById(episode);
        advance(episode);
    }

    /**
     * 手动重试失败步骤
     * <p>
     * 只有 stepStatus == FAILED 时才允许调用。
     * 重置为 PENDING 后重新 advance。
     */
    @Transactional
    public void retry(Long episodeId) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) throw new PipelineException("剧集不存在: " + episodeId);
        if (episode.getStepStatus() != StepStatus.FAILED) {
            throw new PipelineException("当前步骤未失败，无需重试");
        }
        log.info("重试: episodeId={}, status={}, error={}", episodeId, episode.getStatus().getCode(), episode.getErrorMsg());
        episode.setStepStatus(StepStatus.PENDING);
        episodeMapper.updateById(episode);
        advance(episode);
    }

    /**
     * 驳回剧集到指定状态
     * <p>
     * 只允许 StateMachine 定义的合法路径。
     * 当前支持：SCRIPT_REVIEW→SCRIPT_DRAFT（剧本驳回）, EPISODE_APPROVAL→GENERATING（终审驳回）
     */
    @Transactional
    public void reject(Long episodeId, EpisodeStatus target) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) throw new PipelineException("剧集不存在: " + episodeId);
        if (!StateMachine.canTransition(episode.getStatus(), target)) {
            throw new PipelineException("不允许驳回: " + episode.getStatus().getLabel() + " → " + target.getLabel());
        }
        log.info("驳回: episodeId={}, {}→{}", episodeId, episode.getStatus().getLabel(), target.getLabel());
        episodeMapper.updateStatus(episodeId, episode.getStatus().getCode(), target.getCode(), StepStatus.PENDING.getCode());
    }

    /**
     * 创建项目（纯容器，不创建剧集）
     * <p>
     * 所有剧集（包括第一集）由用户手动通过 addEpisode() 添加。
     *
     * @param name       项目名称
     * @param resourceId 可选，从资源库创建时的资源 ID
     * @param createdBy  创建者用户 ID
     */
    @Transactional
    public Project createProject(String name, Long resourceId, Long createdBy) {
        Project project = new Project();
        project.setName(name);
        project.setResourceId(resourceId);
        project.setCreatedBy(createdBy);
        project.setIsPublic(true);
        projectMapper.insert(project);
        log.info("项目创建成功: id={}, name={}, createdBy={}", project.getId(), name, createdBy);
        return project;
    }

    /**
     * 为已有项目添加新剧集
     *
     * @param projectId     所属项目 ID
     * @param title         本集标题
     * @param episodeNumber 集号
     * @return 新建的剧集实体（含自增 id）
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

        // 更新项目的总集数
        project.setEpisodesCount(project.getEpisodesCount() + 1);
        projectMapper.updateById(project);

        log.info("剧集添加成功: projectId={}, episodeId={}, #{}({})", projectId, episode.getId(), episodeNumber, title);
        return episode;
    }

    // ==================== 内部方法 ====================

    /** 判断是否为终态（不需要 Handler 执行） */
    private boolean isTerminal(EpisodeStatus status) {
        for (EpisodeStatus ts : TERMINAL_STATES) {
            if (ts == status) return true;
        }
        return false;
    }

    /**
     * 剧集完成时的收尾逻辑：
     * 1. 设置 stepStatus = SUCCESS
     * 2. 重新统计项目已完成集数并更新 project.completedCount
     */
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
