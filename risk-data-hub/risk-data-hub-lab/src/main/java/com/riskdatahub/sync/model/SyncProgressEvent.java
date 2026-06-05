package com.riskdatahub.sync.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 同步进度事件 — 每个业务每完成一页拉取或落库时发布。
 * <p>由 Spring {@link org.springframework.context.ApplicationEventPublisher} 发布，
 * 通过 {@link org.springframework.context.event.EventListener} 异步消费。</p>
 *
 * @author risk-data-hub
 */
@Data
public class SyncProgressEvent {

    public SyncProgressEvent(Long taskId, String businessCode, int pulledCount, int savedCount) {
        this.taskId = taskId;
        this.businessCode = businessCode;
        this.pulledCount = pulledCount;
        this.savedCount = savedCount;
    }

    /** 同步任务 ID（用于关联到 sync_task 记录） */
    private Long taskId;

    /** 业务编码（STOCK / TRADE / POSITION / ASSET） */
    private String businessCode;

    /** 当前已拉取总数 */
    private int pulledCount;

    /** 当前已落库总数 */
    private int savedCount;

    // ====== 可选耗时指标（落库线程每页完成后携带，为 null 时不更新） ======

    private Long fetchDurationMs;
    private Long transformDurationMs;
    private Long saveDurationMs;
    private Integer fetchPageCount;
    private Integer saveBatchCount;
    private Long maxFetchPageMs;
    private Long maxSaveBatchMs;
    private Long cacheLookupDurationMs;
    private Long batchInsertDurationMs;
    private Long globalIdQueryDurationMs;
    private Long batchUpdateDurationMs;
}
