package com.example.dynamicds.dto;

import com.example.dynamicds.sync.SyncSupport.BusinessSyncResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 同步执行结果 DTO — 替代 Map<String, Object>，接口返回结构一目了然。
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
