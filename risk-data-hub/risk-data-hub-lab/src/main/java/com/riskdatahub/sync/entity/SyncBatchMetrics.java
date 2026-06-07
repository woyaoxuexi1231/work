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

    private LocalDateTime fetchStartedAt;
    private LocalDateTime fetchFinishedAt;
    private LocalDateTime fetchQueuedAt;
    private LocalDateTime processStartedAt;
    private LocalDateTime processFinishedAt;
    private LocalDateTime idGenStartedAt;
    private LocalDateTime idGenFinishedAt;
    private LocalDateTime transformStartedAt;
    private LocalDateTime transformFinishedAt;
    private LocalDateTime saveStartedAt;
    private LocalDateTime cacheLookupStartedAt;
    private LocalDateTime cacheLookupFinishedAt;
    private LocalDateTime insertStartedAt;
    private LocalDateTime insertFinishedAt;
    private LocalDateTime cacheAddStartedAt;
    private LocalDateTime cacheAddFinishedAt;
    private LocalDateTime globalIdQueryStartedAt;
    private LocalDateTime globalIdQueryFinishedAt;
    private LocalDateTime setIdStartedAt;
    private LocalDateTime setIdFinishedAt;
    private LocalDateTime updateStartedAt;
    private LocalDateTime updateFinishedAt;
    private LocalDateTime saveFinishedAt;

    private LocalDateTime recordedAt;
}
