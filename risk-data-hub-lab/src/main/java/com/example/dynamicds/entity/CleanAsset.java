package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.sync.CleanRecordContext;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("clean_asset")
public class CleanAsset {
    @TableId(type = IdType.INPUT)
    private Long globalId;
    private String sourceSystem;
    private String sourceType;
    private Long sourceRowId;
    private String accountName;
    private String accountNo;
    private BigDecimal cashBalance;
    private BigDecimal frozenBalance;
    private BigDecimal totalAsset;
    private String statDay;
    private String cleanBatch;
    private String createdAt;

    public static CleanAsset create(CleanRecordContext context,
                                    String accountName,
                                    String accountNo,
                                    BigDecimal cashBalance,
                                    BigDecimal frozenBalance,
                                    BigDecimal totalAsset,
                                    String statDay) {
        CleanAsset asset = new CleanAsset();
        asset.setGlobalId(context.globalId());
        asset.setSourceSystem(context.sourceSystem());
        asset.setSourceType(context.sourceType());
        asset.setSourceRowId(context.sourceRowId());
        asset.setAccountName(accountName);
        asset.setAccountNo(accountNo);
        asset.setCashBalance(cashBalance);
        asset.setFrozenBalance(frozenBalance);
        asset.setTotalAsset(totalAsset);
        asset.setStatDay(statDay);
        asset.setCleanBatch(context.cleanBatch());
        asset.setCreatedAt(context.createdAt());
        return asset;
    }
}
