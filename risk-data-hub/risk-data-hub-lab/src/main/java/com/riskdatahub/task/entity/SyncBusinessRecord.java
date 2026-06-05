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

    // ====== 耗时指标（毫秒），由 SyncMetrics 采集 ======

    /** 拉取阶段总耗时（所有 fetchPage 累加） */
    private Long fetchDurationMs;

    /** 转换阶段总耗时（所有 transform 累加） */
    private Long transformDurationMs;

    /** 落库阶段总耗时（所有 saveBatch 累加） */
    private Long saveDurationMs;

    /** 拉取页数 */
    private Integer fetchPageCount;

    /** 落库批次数 */
    private Integer saveBatchCount;

    /** 单页最大拉取耗时 */
    private Long maxFetchPageMs;

    /** 单批最大落库耗时 */
    private Long maxSaveBatchMs;

    /** 缓存查询累计耗时（getExistingIds） */
    private Long cacheLookupDurationMs;

    /** 批量 INSERT 累计耗时 */
    private Long batchInsertDurationMs;

    /** 查询 globalId 累计耗时 */
    private Long globalIdQueryDurationMs;

    /** 批量 UPDATE 累计耗时 */
    private Long batchUpdateDurationMs;
}
