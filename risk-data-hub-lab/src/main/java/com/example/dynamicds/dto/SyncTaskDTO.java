package com.example.dynamicds.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同步任务状态 DTO — 替代 Map<String, Object>，接口返回结构一目了然。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncTaskDTO {
    /** 任务唯一标识 */
    private String taskId;
    /** 任务状态：IDLE / QUEUED / RUNNING / SUCCESS / FAILED */
    private String status;
    /** 进度百分比 0-100 */
    private int progress;
    /** 数据源标识 */
    private String dataSourceKey;
    /** 数据源名称 */
    private String dataSourceName;
    /** 数据源类型 */
    private String datasourceType;
    /** 分页大小 */
    private Integer pageSize;
    /** 累计拉取记录数 */
    private Integer totalPulledCount;
    /** 累计落库记录数 */
    private Integer totalSavedCount;
    /** 任务提交时间 */
    private String submittedAt;
    /** 任务开始执行时间 */
    private String startedAt;
    /** 任务结束时间 */
    private String finishedAt;
    /** 状态描述信息 */
    private String message;
    /** 错误信息 */
    private String errorMessage;
    /** 任务是否正在运行中 */
    private boolean running;
}
