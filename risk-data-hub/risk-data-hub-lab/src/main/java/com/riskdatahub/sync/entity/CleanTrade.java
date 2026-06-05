package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.riskdatahub.sync.model.CleanRecordContext;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 中台清洗后交易记录实体。
 * <p>
 * 对应数据库表 {@code clean_trade}，存储经 ETL 清洗后的标准化交易数据。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("clean_trade")
public class CleanTrade {

    @TableId(type = IdType.INPUT)
    private Long globalId;

    /** 来源系统标识 */
    private String sourceSystem;

    /** 来源系统类型 */
    private String sourceType;

    /** 来源系统记录 ID */
    private Long sourceRowId;

    /** 业务单号 */
    private String vendorTradeNo;

    /** 业务类型 */
    private String bizType;

    /** 交易方向（BUY / SELL） */
    private String direction;

    /** 交易金额 */
    private BigDecimal amount;

    /** 状态中文名称 */
    private String statusName;

    /** 对手方名称 */
    private String counterpartyName;

    /** 清洗方式 */
    private String cleanMode;

    /** 清洗批次号 */
    private String cleanBatch;

    /** 交易时间 */
    private LocalDateTime tradeTime;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /**
     * 工厂方法 — 从清洗上下文和源数据构造中台实体。
     *
     * @param context          清洗记录上下文
     * @param vendorTradeNo    业务单号
     * @param bizType          业务类型
     * @param direction        交易方向
     * @param amount           交易金额
     * @param statusName       状态中文名称
     * @param counterpartyName 对手方名称
     * @param cleanMode        清洗方式
     * @param tradeTime        交易时间
     * @return 中台交易实体
     */
    public static CleanTrade create(CleanRecordContext context,
                                    String vendorTradeNo,
                                    String bizType,
                                    String direction,
                                    BigDecimal amount,
                                    String statusName,
                                    String counterpartyName,
                                    String cleanMode,
                                    LocalDateTime tradeTime) {
        CleanTrade trade = new CleanTrade();
        trade.setGlobalId(context.getGlobalId());
        trade.setSourceSystem(context.getSourceSystem());
        trade.setSourceType(context.getSourceType());
        trade.setSourceRowId(context.getSourceRowId());
        trade.setVendorTradeNo(vendorTradeNo);
        trade.setBizType(bizType);
        trade.setDirection(direction);
        trade.setAmount(amount);
        trade.setStatusName(statusName);
        trade.setCounterpartyName(counterpartyName);
        trade.setCleanMode(cleanMode);
        trade.setCleanBatch(context.getCleanBatch());
        trade.setTradeTime(tradeTime);
        trade.setCreatedAt(context.getCreatedAt());
        return trade;
    }
}
