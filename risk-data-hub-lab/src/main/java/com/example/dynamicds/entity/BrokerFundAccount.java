package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
}
