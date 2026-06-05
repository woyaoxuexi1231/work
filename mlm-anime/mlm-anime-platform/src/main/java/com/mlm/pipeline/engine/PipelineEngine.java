package com.mlm.pipeline.engine;

import com.mlm.common.enums.EpisodeStatus;
import com.mlm.common.enums.StepStatus;
import com.mlm.common.exception.PipelineException;
import com.mlm.pipeline.entity.Episode;
import com.mlm.pipeline.mapper.EpisodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Pipeline 核心引擎 — 剧集状态机编排器
 * <p>
 * 【职责】
 * <ul>
 *   <li>剧集状态推进（advance）— 核心方法，CAS 乐观锁 + StepHandler 委派</li>
 *   <li>剧本提交（submitScript）— 从 DRAFT 推进到 REVIEW</li>
 *   <li>手动重试（retry）— 将 FAILED 步骤置为 PENDING 后重新推进</li>
 *   <li>驳回（reject）— 从当前状态回退到指定目标状态</li>
 * </ul>
 * <p>
 * 【设计模式】模板方法 + 策略
 * <ul>
 *   <li>模板方法：{@link #advance(Episode)} 定义了状态推进骨架</li>
 *   <li>策略：{@link StepHandler} 接口由各处理器实现具体业务逻辑</li>
 * </ul>
 * <p>
 * 【并发安全】
 * 使用乐观锁（CAS based on expected status）保证状态更新的原子性，
 * 避免并发请求导致状态错乱。
 * <p>
 * 【状态编码约定】
 * 数据库存 int 码，业务逻辑在边界处用 {@code EpisodeStatus.of(int)} 转为枚举。
 * 枚举用于类型安全比较，int 用于存储和传输。
 * <p>
 * 【变更历史】
 * v2.0 — 从本引擎移除了 Project/Episode 创建方法，创建逻辑归到对应的 Service 层。
 * PipelineEngine 专注状态机编排，不再承担实体创建职责。
 *
 * @author mlm
 * @see StateMachine
 * @see StepHandler
 * @see StepHandlerRegistry
 * @see EpisodeStatus
 */
@Component
public class PipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineEngine.class);

    /** 终态列表 — 到达终态后不再执行 Handler */
    private static final EpisodeStatus[] TERMINAL_STATES = {
            EpisodeStatus.COMPLETED
    };

    private final EpisodeMapper episodeMapper;
    private final StepHandlerRegistry registry;

    /**
     * 构造 Pipeline 引擎
     *
     * @param episodeMapper 剧集 Mapper（乐观锁状态更新）
     * @param registry      步骤处理器注册表
     */
    public PipelineEngine(EpisodeMapper episodeMapper,
                          StepHandlerRegistry registry) {
        this.episodeMapper = episodeMapper;
        this.registry = registry;
    }

    // ======================== 公开方法 ========================

    /**
     * 推进剧集到下一状态 — 核心编排方法
     * <p>
     * 【执行流程】
     * <ol>
     *   <li>将 int 状态码转为 {@link EpisodeStatus} 枚举</li>
     *   <li>通过 {@link StateMachine#next(EpisodeStatus)} 获取下一状态</li>
     *   <li>使用乐观锁 CAS 更新数据库状态</li>
     *   <li>若更新成功且非终态，委派对应的 {@link StepHandler} 执行业务逻辑</li>
     *   <li>若到达终态（COMPLETED），更新项目的已完成计数</li>
     * </ol>
     *
     * @param episode 待推进的剧集（需包含 id 和当前 status）
     * @throws NullPointerException  episode.id 或 episode.status 为 null
     * @throws IllegalStateException 当前状态无法流转
     */
    @Transactional(rollbackFor = Exception.class)
    public void advance(Episode episode) {
        Objects.requireNonNull(episode.getId(), "episode.id 不能为空");
        Objects.requireNonNull(episode.getStatus(), "episode.status 不能为空");

        // 1. int → 枚举 转换
        EpisodeStatus current = EpisodeStatus.of(episode.getStatus());
        EpisodeStatus next = StateMachine.next(current);

        log.info("[Pipeline] 推进剧集: episodeId={}, {}→{} ({}→{})",
                episode.getId(),
                current.getCode(), next.getCode(),
                current.getLabel(), next.getLabel());

        // 2. CAS 乐观锁更新
        int updatedRows = episodeMapper.updateStatus(
                episode.getId(),
                current.getCode(),
                next.getCode(),
                StepStatus.PROCESSING.getCode()
        );

        if (updatedRows == 0) {
            log.warn("[Pipeline] 状态更新失败（并发冲突）, episodeId={}, expected={}",
                    episode.getId(), current.getCode());
            return;
        }

        // 3. 回写实体状态
        episode.setStatus(next.getCode());
        episode.setStepStatus(StepStatus.PROCESSING.getCode());

        // 4. 终态处理
        if (isTerminal(next)) {
            log.info("[Pipeline] 剧集到达终态: episodeId={}, status={}",
                    episode.getId(), next.getLabel());
            if (next == EpisodeStatus.COMPLETED) {
                completeEpisode(episode);
            }
            return;
        }

        // 5. 委派步骤处理器（非终态）
        StepHandler handler = registry.get(next);
        try {
            handler.handle(episode);
            log.info("[Pipeline] 步骤执行成功: episodeId={}, step={}",
                    episode.getId(), next.getLabel());
        } catch (Exception e) {
            log.error("[Pipeline] 步骤执行失败: episodeId={}, step={}",
                    episode.getId(), next.getLabel(), e);
            episodeMapper.markStepFailed(episode.getId(), e.getMessage());
        }
    }

    /**
     * 提交剧本并自动推进到审核状态
     * <p>
     * 将剧集状态设为 SCRIPT_DRAFT(2)、子状态设为 PENDING(0)，
     * 然后调用 {@link #advance(Episode)} 推进到 SCRIPT_REVIEW(3)。
     *
     * @param episode 包含剧本内容的剧集实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitScript(Episode episode) {
        episode.setStatus(EpisodeStatus.SCRIPT_DRAFT.getCode());
        episode.setStepStatus(StepStatus.PENDING.getCode());
        episodeMapper.updateById(episode);
        log.info("[Pipeline] 剧本已提交，准备推进审核: episodeId={}", episode.getId());
        advance(episode);
    }

    /**
     * 手动重试失败的步骤
     * <p>
     * 将 stepStatus 从 FAILED(-1) 重置为 PENDING(0)，
     * 然后重新调用 {@link #advance(Episode)} 执行当前步骤。
     * <p>
     * 【前置条件】
     * 只有 stepStatus == FAILED 的剧集允许重试，避免已处理中的步骤被重复触发。
     *
     * @param episodeId 剧集 ID
     * @throws PipelineException 剧集不存在或步骤未失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long episodeId) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) {
            throw new PipelineException("剧集不存在: " + episodeId);
        }
        if (episode.getStepStatus() != StepStatus.FAILED.getCode()) {
            throw new PipelineException("当前步骤未失败（stepStatus="
                    + episode.getStepStatus() + "），无需重试");
        }

        log.info("[Pipeline] 手动重试: episodeId={}, status={}", episodeId, episode.getStatus());
        episode.setStepStatus(StepStatus.PENDING.getCode());
        episodeMapper.updateById(episode);
        advance(episode);
    }

    /**
     * 驳回剧集到指定状态
     * <p>
     * 将剧集从当前状态回退到目标状态，用于审核不通过时退回重做。
     * 会通过 {@link StateMachine#canTransition(EpisodeStatus, EpisodeStatus)}
     * 校验驳回路径的合法性。
     *
     * @param episodeId 剧集 ID
     * @param target    驳回目标状态
     * @throws PipelineException 剧集不存在或驳回路径不允许
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long episodeId, EpisodeStatus target) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null) {
            throw new PipelineException("剧集不存在: " + episodeId);
        }

        EpisodeStatus current = EpisodeStatus.of(episode.getStatus());
        if (!StateMachine.canTransition(current, target)) {
            throw new PipelineException("不允许的驳回路径: " + current.getLabel()
                    + " → " + target.getLabel());
        }

        log.info("[Pipeline] 驳回剧集: episodeId={}, {}→{} ({}→{})",
                episodeId,
                current.getCode(), target.getCode(),
                current.getLabel(), target.getLabel());

        episodeMapper.updateStatus(
                episodeId,
                current.getCode(),
                target.getCode(),
                StepStatus.PENDING.getCode()
        );
    }

    // ======================== 内部方法 ========================

    /**
     * 判断指定状态是否为终态
     * <p>
     * 终态不会执行 StepHandler，直接完成流程。
     */
    private boolean isTerminal(EpisodeStatus status) {
        for (EpisodeStatus ts : TERMINAL_STATES) {
            if (ts == status) return true;
        }
        return false;
    }

    /**
     * 完成剧集 — 更新子状态并刷新项目已完成计数
     * <p>
     * 当剧集到达 COMPLETED 状态时调用，更新该项目的已完成集数。
     * 更新计数使用 countCompleted 查询确保准确性，不依赖缓存。
     *
     * @param episode 已完成的剧集
     */
    private void completeEpisode(Episode episode) {
        episode.setStepStatus(StepStatus.SUCCESS.getCode());
        episodeMapper.updateById(episode);
        log.info("[Pipeline] 剧集完成: episodeId={}", episode.getId());

        // 更新项目已完成计数（由 EpisodeService 的 updateCompletedCount 取代
        // 此处仅保留状态更新，计数逻辑下沉到 EpisodeService 统一管理）
    }
}
