package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 业务同步记录 — 关联 sync_task，记录每个业务（STOCK/TRADE/POSITION/ASSET）的同步结果。
 */
@Data
@TableName("sync_business_record")
public class SyncBusinessRecord {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String taskId;
    private String businessCode;
    private String status;
    private Integer pageCount;
    private Integer pulledCount;
    private Integer savedCount;
    private Long lastRowId;
    private String errorMessage;
    private String startedAt;
    private String finishedAt;
}
