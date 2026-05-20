package com.mlm.pipeline.engine;

import com.mlm.common.enums.EpisodeStatus;
import com.mlm.pipeline.entity.Episode;

/**
 * 步骤处理器接口 — Pipeline 中各步骤的业务逻辑抽象（按集）
 * <p>
 * 每个 {@link EpisodeStatus} 对应一个 Handler 实现。
 * 策略模式，新增步骤只需实现此接口。
 */
public interface StepHandler {

    /** 该 Handler 对应的剧集状态 */
    EpisodeStatus step();

    /**
     * 执行业务逻辑
     *
     * @param episode 当前剧集
     */
    void handle(Episode episode);
}
