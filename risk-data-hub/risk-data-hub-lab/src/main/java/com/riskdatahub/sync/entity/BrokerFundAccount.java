package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Broker 券商系统资金账户实体。
 * <p>
 * 对应数据库表 {@code broker_fund_account}，记录 Broker 系统的资金账户信息。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("broker_fund_account")
public class BrokerFundAccount {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 客户全称 */
    private String clientFullName;

    /** 资金账号 */
    private String fundAccountNo;

    /** 当前余额 */
    private BigDecimal currentBalance;

    /** 冻结资金 */
    private BigDecimal frozenCapital;

    /** 总资产 */
    private BigDecimal totalAsset;

    /** 业务日期 */
    private String bizDate;

    /** 同步标记：0-未同步，1-已同步 */
    private Integer syncFlag;
}
