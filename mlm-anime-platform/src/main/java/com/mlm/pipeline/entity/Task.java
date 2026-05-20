package com.mlm.pipeline.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.mlm.common.enums.ModelType;
import com.mlm.common.enums.StepStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 生成任务实体 — 对应数据库 task 表
 * <p>
 * 每个任务对应一次 AI 模型调用（文生文/文生图/图生视频）。
 * 任务提交给厂商后进入 PROCESSING 状态，由 {@code TaskPollingScheduler}
 * 定时轮询厂商接口获取结果，成功/失败后更新状态并检查所属步骤是否全部完成。
 *
 * @see com.mlm.pipeline.scheduler.TaskPollingScheduler
 * @see com.mlm.model.core.ModelGateway
 */
@Data
@NoArgsConstructor
@TableName("task")
public class Task {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属剧集 ID */
    private Long episodeId;

    /** 所属步骤标识（如 GENERATING） */
    private String step;

    /** 模型类型：文生文 / 文生图 / 图生视频 */
    private ModelType modelType;

    /** 厂商标识：openai / stable_diffusion / kling */
    private String vendor;

    /** 厂商侧的任务 ID（submit 后返回） */
    private String vendorTaskId;

    /** 任务状态（int 码, -1=失败, 0=待处理, 1=成功, 2=处理中） */
    private Integer status = 0;

    /** 提交给厂商的请求参数 JSON */
    private String requestJson;

    /** 厂商返回的结果 JSON */
    private String resultJson;

    /** 已轮询次数 */
    private Integer pollCount = 0;

    /** 最大允许轮询次数（超限标记 FAILED） */
    private Integer maxPollCount = 60;

    /** 轮询间隔（秒） */
    private Integer pollInterval = 30;

    /** 下次可轮询时间（用于退避） */
    private LocalDateTime nextPollAt;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 判断当前是否已到轮询时间
     *
     * @param intervalSeconds 轮询间隔
     * @return true = 可以发起查询
     */
    public boolean shouldPollNow(int intervalSeconds) {
        if (nextPollAt == null) return true;
        return LocalDateTime.now().isAfter(nextPollAt);
    }

    /**
     * 计算下次轮询时间（当前时间 + 间隔）
     *
     * @param intervalSeconds 轮询间隔（秒）
     */
    public void scheduleNextPoll(int intervalSeconds) {
        this.nextPollAt = LocalDateTime.now().plusSeconds(intervalSeconds);
    }
}
