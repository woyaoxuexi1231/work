package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 同步任务记录 — 替代原内存中的 SyncTaskSnapshot。
 */
@Data
@TableName("sync_task")
public class SyncTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String dataSourceKey;
    private String dataSourceName;
    private String datasourceType;
    private Integer pageSize;
    private String status;
    private Integer progress;
    private Integer totalPulledCount;
    private Integer totalSavedCount;
    private String submittedAt;
    private String startedAt;
    private String finishedAt;
    private String message;
    private String errorMessage;
}
