package com.riskdatahub.sync.model;

import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 同步执行结果 DTO — 包含数据源信息和各业务类型的详细同步结果。
 *
 * @author risk-data-hub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncResultDTO {

    /** 数据源标识 */
    private String dataSourceKey;

    /** 数据源名称 */
    private String dataSourceName;

    /** 数据源类型 */
    private String datasourceType;

    /** 分页大小 */
    private int pageSize;

    /** 本次同步批次号 */
    private String batchNo;

    /** 业务模板执行的最大页数 */
    private int pageCount;

    /** 累计拉取记录数 */
    private int pulledCount;

    /** 累计落库记录数 */
    private int savedCount;

    /** 各业务类型的详细结果 */
    private Map<String, BusinessSyncResult> businessResults;
}
