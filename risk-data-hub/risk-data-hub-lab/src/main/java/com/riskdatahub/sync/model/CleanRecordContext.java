package com.riskdatahub.sync.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 清洗记录上下文 — 中台表每条记录的来源追溯信息。
 * <p>
 * 记录了该条数据从哪个源系统、哪个批次、哪条原始记录清洗而来，
 * 用于数据追溯和问题排查。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@AllArgsConstructor
public class CleanRecordContext {

    /** 中台表全局唯一 ID（Leaf 号段生成） */
    private Long globalId;

    /** 源系统标识（数据源 key） */
    private String sourceSystem;

    /** 源系统类型（TRADE_OMS / TRADE_BROKER） */
    private String sourceType;

    /** 源系统记录 ID */
    private Long sourceRowId;

    /** 清洗批次号 */
    private String cleanBatch;

    /** 记录创建时间 */
    private String createdAt;
}
