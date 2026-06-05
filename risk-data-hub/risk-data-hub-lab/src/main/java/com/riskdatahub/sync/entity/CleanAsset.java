package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.riskdatahub.sync.model.CleanRecordContext;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 中台清洗后资金资产实体。
 * <p>
 * 对应数据库表 {@code clean_asset}，存储经 ETL 清洗后的标准化资金数据。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("clean_asset")
public class CleanAsset {

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

    /** 资金账号 */
    private String accountNo;

    /** 资金余额 */
    private BigDecimal cashBalance;

    /** 冻结资金 */
    private BigDecimal frozenBalance;

    /** 总资产 */
    private BigDecimal totalAsset;

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
     * @param accountNo    资金账号
     * @param cashBalance  资金余额
     * @param frozenBalance 冻结资金
     * @param totalAsset   总资产
     * @param statDay      统计日期
     * @return 中台资金实体
     */
    public static CleanAsset create(CleanRecordContext context,
                                    String accountName,
                                    String accountNo,
                                    BigDecimal cashBalance,
                                    BigDecimal frozenBalance,
                                    BigDecimal totalAsset,
                                    String statDay) {
        CleanAsset asset = new CleanAsset();
        asset.setGlobalId(context.getGlobalId());
        asset.setSourceSystem(context.getSourceSystem());
        asset.setSourceType(context.getSourceType());
        asset.setSourceRowId(context.getSourceRowId());
        asset.setAccountName(accountName);
        asset.setAccountNo(accountNo);
        asset.setCashBalance(cashBalance);
        asset.setFrozenBalance(frozenBalance);
        asset.setTotalAsset(totalAsset);
        asset.setStatDay(statDay);
        asset.setCleanBatch(context.getCleanBatch());
        asset.setCreatedAt(context.getCreatedAt());
        return asset;
    }
}
