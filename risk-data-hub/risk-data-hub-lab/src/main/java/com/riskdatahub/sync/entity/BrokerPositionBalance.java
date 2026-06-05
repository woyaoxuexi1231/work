package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Broker 券商系统持仓余额实体。
 * <p>
 * 对应数据库表 {@code broker_position_balance}，记录 Broker 系统的持仓及余额信息。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("broker_position_balance")
public class BrokerPositionBalance {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 客户全称 */
    private String clientFullName;

    /** 证券代码 */
    private String secuCode;

    /** 当前持仓量 */
    private Long currentVolume;

    /** 可用数量 */
    private Long enableVolume;

    /** 成本价 */
    private BigDecimal costPx;

    /** 市值 */
    private BigDecimal marketAmt;

    /** 业务日期 */
    private String bizDate;

    /** 同步标记：0-未同步，1-已同步 */
    private Integer syncFlag;
}
