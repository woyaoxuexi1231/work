package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Broker 券商系统成交记录实体。
 * <p>
 * 对应数据库表 {@code broker_trade_deal}，记录 Broker 系统的成交数据。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("broker_trade_deal")
public class BrokerTradeDeal {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 成交编码 */
    private String dealCode;

    /** 证券代码 */
    private String secuCode;

    /** 客户全称 */
    private String clientFullName;

    /** 买卖方向标记（1-买入，2-卖出） */
    private String bsFlag;

    /** 成交数量 */
    private Long dealVolume;

    /** 成交价格 */
    private BigDecimal dealPrice;

    /** 成交金额 */
    private BigDecimal turnoverAmount;

    /** 状态标记（A-待确认，S-已成交，X-已撤单） */
    private String statusMark;

    /** 成交时间 */
    private LocalDateTime dealAt;

    /** 同步标记：0-未同步，1-已同步 */
    private Integer syncFlag;
}
