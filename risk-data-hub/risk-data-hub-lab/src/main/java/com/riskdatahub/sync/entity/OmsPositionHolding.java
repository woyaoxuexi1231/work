package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * OMS 系统持仓记录实体。
 * <p>
 * 对应数据库表 {@code oms_position_holding}，记录 OMS 系统的持仓信息。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("oms_position_holding")
public class OmsPositionHolding {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 投资者名称 */
    private String investorName;

    /** 股票代码 */
    private String stockCode;

    /** 持有数量 */
    private Long holdingQty;

    /** 可用数量 */
    private Long availableQty;

    /** 成本价 */
    private BigDecimal costPrice;

    /** 市值 */
    private BigDecimal marketValue;

    /** 统计日期 */
    private String statDay;

    /** 同步标记：0-未同步，1-已同步 */
    private Integer syncFlag;
}
