package com.riskdatahub.sync.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * 同步上下文 — 携带当前同步任务的参数。
 * <p>
 * 不可变对象（Immutable Object），在多线程环境下（{@link com.riskdatahub.sync.SyncOrchestrator}
 * 并发派发多个 Future），每个线程读到的是同一份上下文，不会出现某个线程修改了 pageSize
 * 导致其他线程行为变化。
 * </p>
 *
 * @author risk-data-hub
 */
@Getter
@Builder
public class BusinessSyncContext {

    /** 数据源 key（如 trade_oms / trade_broker） */
    private final String dataSourceKey;

    /** 数据源类型（TRADE_OMS / TRADE_BROKER） */
    private final String datasourceType;

    /** 每页拉取行数 */
    private final int pageSize;

    /** 同步批次号（SYNC-时间戳） */
    private final String batchNo;

    /** 同步任务 ID（用于进度事件关联 sync_task 记录） */
    private final Long taskId;

    /** 每个业务的上一次成功游标，用于断点续传（businessCode → lastRowId） */
    @Builder.Default
    private final Map<String, Long> initialCursors = Collections.emptyMap();
}
