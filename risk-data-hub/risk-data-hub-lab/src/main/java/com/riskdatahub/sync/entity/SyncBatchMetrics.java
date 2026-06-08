package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 批次时间节点 — 仅用于 sync_batch_metrics 表持久化 */
@Data
@TableName("sync_batch_metrics")
public class SyncBatchMetrics {

    private Long id;
    private Long recordId;
    private Integer batchNo;

    private Integer pulledCount;
    private Integer savedCount;
    private Integer insertCount;
    private Integer updateCount;

    /** 拉取上游数据开始节点 */
    private LocalDateTime fetchStartedAt;
    /** 拉取上游数据结束节点 */
    private LocalDateTime fetchFinishedAt;
    /** 上游数据进入阻塞队列的时间节点 */
    private LocalDateTime fetchQueuedAt;
    /** 上游数据移出阻塞队列的时间节点 */
    private LocalDateTime fetchQueuedFinishedAt;
    /** 为上游数据生成分布式ID的时间节点 */
    private LocalDateTime idGenStartedAt;
    /** 为上游数据生成分布式ID结束节点 */
    private LocalDateTime idGenFinishedAt;
    /** 数据转换开始 */
    private LocalDateTime transformStartedAt;
    /** 数据转换结束 */
    private LocalDateTime transformFinishedAt;
    /** 查询已存在的数据 开始时间 */
    private LocalDateTime existingQueryStartedAt;
    /** 查询已存在的数据 结束时间 */
    private LocalDateTime existingQueryFinishedAt;
    /** 数据拆分开始 */
    private LocalDateTime splitStartedAt;
    /** 数据拆分结束 */
    private LocalDateTime splitFinishedAt;
    /** 插入新数据开始 */
    private LocalDateTime insertStartedAt;
    /** 插入新数据结束 */
    private LocalDateTime insertFinishedAt;
    /** 更新已存在数据开始 */
    private LocalDateTime updateStartedAt;
    /** 批量更新已存在数据结束 */
    private LocalDateTime updateFinishedAt;
    /** 异常信息 */
    private String errorMessage;

    private LocalDateTime recordedAt;
}
