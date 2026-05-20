package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("broker_fund_account")
public class BrokerFundAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clientFullName;
    private String fundAccountNo;
    private BigDecimal currentBalance;
    private BigDecimal frozenCapital;
    private BigDecimal totalAsset;
    private String bizDate;
    private Integer syncFlag;
}
