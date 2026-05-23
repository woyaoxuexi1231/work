package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.bootstrap.MarketSeedSnapshot;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("broker_fund_account")
public class BrokerFundAccount {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String clientFullName;
    private String fundAccountNo;
    private BigDecimal currentBalance;
    private BigDecimal frozenCapital;
    private BigDecimal totalAsset;
    private String bizDate;
    private Integer syncFlag;

    public static BrokerFundAccount fromSeed(Long id, MarketSeedSnapshot seed,
                                             String clientFullName, String fundAccountNo) {
        BigDecimal base = seed.tradePrice(3).multiply(BigDecimal.valueOf(12000));
        BrokerFundAccount account = new BrokerFundAccount();
        account.setId(id);
        account.setClientFullName(clientFullName);
        account.setFundAccountNo(fundAccountNo);
        account.setCurrentBalance(base.setScale(2, RoundingMode.HALF_UP));
        account.setFrozenCapital(base.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP));
        account.setTotalAsset(base.multiply(BigDecimal.valueOf(1.65)).setScale(2, RoundingMode.HALF_UP));
        account.setBizDate(seed.tradeDay());
        account.setSyncFlag(0);
        return account;
    }
}
