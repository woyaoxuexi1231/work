package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * OMS 系统资金资产实体。
 * <p>
 * 对应数据库表 {@code oms_cash_asset}，记录 OMS 系统的资金账户信息。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("oms_cash_asset")
public class OmsCashAsset {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 投资者名称 */
    private String investorName;

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

    /** 同步标记：0-未同步，1-已同步 */
    private Integer syncFlag;
}
