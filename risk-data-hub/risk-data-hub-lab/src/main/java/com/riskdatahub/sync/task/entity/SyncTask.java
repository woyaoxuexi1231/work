package com.riskdatahub.sync.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务实体 — 记录每次同步任务的执行状态。
 * <p>
 * 对应数据库表 {@code sync_task}，追踪从提交到完成的完整生命周期。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("sync_task")
public class SyncTask {

    /** 任务 ID（Leaf 号段生成） */
    private Long id;

    /** 数据源 key */
    private String dataSourceKey;

    /** 数据源名称 */
    private String dataSourceName;

    /** 数据源类型 */
    private String datasourceType;

    /** 分页大小 */
    private Integer pageSize;

    /** 同步类型：FULL 全量 / INCREMENTAL 增量 */
    private String syncType;

    /** 任务状态（QUEUED / RUNNING / SUCCESS / FAILED / IDLE） */
    private String status;

    /** 进度百分比（0-100） */
    private Integer progress;

    /** 累计拉取记录数 */
    private Integer totalPulledCount;

    /** 累计落库记录数 */
    private Integer totalSavedCount;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 状态描述信息 */
    private String message;

    /** 错误信息 */
    private String errorMessage;

    /** 运行时标记：是否正在运行（非数据库字段） */
    private transient boolean running;
}
