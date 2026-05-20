package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 同步任务记录
 */
@Data
@TableName("sync_task")
public class SyncTask {
    @TableId(type = IdType.INPUT)
    private Long id;
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
    /** 任务是否正在运行 — 由 status 计算，非数据库字段 */
    @TableField(exist = false)
    private boolean running;
}
