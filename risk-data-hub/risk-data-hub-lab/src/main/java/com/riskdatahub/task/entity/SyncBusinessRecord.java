package com.riskdatahub.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步业务记录 — 记录每次同步任务中各业务类型的执行详情。
 * <p>
 * 对应数据库表 {@code sync_business_record}，一个同步任务可以对应多条业务记录。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("sync_business_record")
public class SyncBusinessRecord {

    /** 记录 ID（Leaf 号段生成） */
    private Long id;

    /** 关联的同步任务 ID */
    private Long taskId;

    /** 业务编码（STOCK / TRADE / POSITION / ASSET） */
    private String businessCode;

    /** 执行状态（SUCCESS / FAILED） */
    private String status;

    /** 拉取页数 */
    private Integer pageCount;

    /** 拉取记录数 */
    private Integer pulledCount;

    /** 落库记录数 */
    private Integer savedCount;

    /** 最后一条记录的游标 ID */
    private Long lastRowId;

    /** 错误信息 */
    private String errorMessage;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

}
