package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Broker 券商系统股票行情实体。
 * <p>
 * 对应数据库表 {@code broker_stock_quote}，记录 Broker 系统的股票报价信息。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("broker_stock_quote")
public class BrokerStockQuote {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 报价编码 */
    private String quoteCode;

    /** 证券代码 */
    private String secuCode;

    /** 交易日 */
    private String tradeDay;

    /** 交易所名称 */
    private String exchangeName;

    /** 开盘价 */
    private BigDecimal openPx;

    /** 最高价 */
    private BigDecimal highPx;

    /** 最低价 */
    private BigDecimal lowPx;

    /** 收盘价 */
    private BigDecimal closePx;

    /** 成交量（手） */
    private Long volNum;

    /** 成交额 */
    private BigDecimal turnoverAmt;

    /** 同步标记：0-未同步，1-已同步 */
    private Integer syncFlag;
}
