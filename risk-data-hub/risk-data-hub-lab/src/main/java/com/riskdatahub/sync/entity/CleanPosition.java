package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.riskdatahub.sync.model.CleanRecordContext;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 中台清洗后持仓记录实体。
 * <p>
 * 对应数据库表 {@code clean_position}，存储经 ETL 清洗后的标准化持仓数据。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("clean_position")
public class CleanPosition {

    @TableId(type = IdType.INPUT)
    private Long globalId;

    /** 来源系统标识 */
    private String sourceSystem;

    /** 来源系统类型 */
    private String sourceType;

    /** 来源系统记录 ID */
    private Long sourceRowId;

    /** 账户名称 */
    private String accountName;

    /** 股票代码 */
    private String stockCode;

    /** 持有数量 */
    private Long holdingQty;

    /** 可用数量 */
    private Long availableQty;

    /** 成本价 */
    private BigDecimal costPrice;

    /** 市值 */
    private BigDecimal marketValue;

    /** 统计日期 */
    private String statDay;

    /** 清洗批次号 */
    private String cleanBatch;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /**
     * 工厂方法 — 从清洗上下文和源数据构造中台实体。
     *
     * @param context      清洗记录上下文
     * @param accountName  账户名称
     * @param stockCode    股票代码
     * @param holdingQty   持有数量
     * @param availableQty 可用数量
     * @param costPrice    成本价
     * @param marketValue  市值
     * @param statDay      统计日期
     * @return 中台持仓实体
     */
    public static CleanPosition create(CleanRecordContext context,
                                       String accountName,
                                       String stockCode,
                                       Long holdingQty,
                                       Long availableQty,
                                       BigDecimal costPrice,
                                       BigDecimal marketValue,
                                       String statDay) {
        CleanPosition position = new CleanPosition();
        position.setGlobalId(context.getGlobalId());
        position.setSourceSystem(context.getSourceSystem());
        position.setSourceType(context.getSourceType());
        position.setSourceRowId(context.getSourceRowId());
        position.setAccountName(accountName);
        position.setStockCode(stockCode);
        position.setHoldingQty(holdingQty);
        position.setAvailableQty(availableQty);
        position.setCostPrice(costPrice);
        position.setMarketValue(marketValue);
        position.setStatDay(statDay);
        position.setCleanBatch(context.getCleanBatch());
        position.setCreatedAt(context.getCreatedAt());
        return position;
    }
}
