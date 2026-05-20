package com.example.dynamicds.sync;

import lombok.Builder;
import lombok.Getter;

/**
 * 模板执行时需要的公共上下文。
 * 业务模板只关心“当前同步的是谁、页大小多少、批次号多少”，避免方法参数一路传到底。
 */
@Getter
@Builder
public class BusinessSyncContext {

    private final String dataSourceKey;
    private final String datasourceType;
    private final int pageSize;
    private final String batchNo;
}
