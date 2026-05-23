package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.bootstrap.MarketSeedSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@TableName("oms_cash_asset")
public class OmsCashAsset {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String investorName;
    private String accountNo;
    private BigDecimal cashBalance;
    private BigDecimal frozenBalance;
    private BigDecimal totalAsset;
    private String statDay;
    private Integer syncFlag;

    public static OmsCashAsset fromSeed(Long id, MarketSeedSnapshot seed,
                                        String investorName, String accountNo) {
        BigDecimal base = seed.tradePrice(2).multiply(BigDecimal.valueOf(10000));
        OmsCashAsset asset = new OmsCashAsset();
        asset.setId(id);
        asset.setInvestorName(investorName);
        asset.setAccountNo(accountNo);
        asset.setCashBalance(base.setScale(2, RoundingMode.HALF_UP));
        asset.setFrozenBalance(base.multiply(BigDecimal.valueOf(0.08)).setScale(2, RoundingMode.HALF_UP));
        asset.setTotalAsset(base.multiply(BigDecimal.valueOf(1.75)).setScale(2, RoundingMode.HALF_UP));
        asset.setStatDay(seed.tradeDay());
        asset.setSyncFlag(0);
        return asset;
    }
}
