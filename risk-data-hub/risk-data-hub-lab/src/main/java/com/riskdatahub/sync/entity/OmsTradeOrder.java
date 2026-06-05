package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * OMS 系统交易订单实体。
 * <p>
 * 对应数据库表 {@code oms_trade_order}，记录 OMS 系统的交易委托订单。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("oms_trade_order")
public class OmsTradeOrder {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 股票代码 */
    private String stockCode;

    /** 投资者名称 */
    private String investorName;

    /** 买卖方向代码（B-买入，S-卖出） */
    private String sideCode;

    /** 交易数量 */
    private Long tradeQty;

    /** 交易价格 */
    private BigDecimal tradePrice;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 交易状态（NEW/DONE/CANCEL） */
    private String tradeStatus;

    /** 交易时间 */
    private String tradeTime;

    /** 同步标记：0-未同步，1-已同步 */
    private Integer syncFlag;
}
