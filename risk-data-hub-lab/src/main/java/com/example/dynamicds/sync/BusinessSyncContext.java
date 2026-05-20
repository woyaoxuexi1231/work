package com.example.dynamicds.sync;

import lombok.Builder;
import lombok.Getter;

/**
 * 同步上下文 — 携带当前同步任务的关键参数传递给模板方法。
 * 包含：数据源标识、数据源类型、分页大小、批次号。
 * 避免将多个参数逐个传递到模板的各个抽象方法中。
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
}
