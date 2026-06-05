package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * OMS 系统股票快照实体。
 * <p>
 * 对应数据库表 {@code oms_stock_snapshot}，记录 OMS 系统的股票行情数据，
 * 通过 sync_flag 控制增量同步。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("oms_stock_snapshot")
public class OmsStockSnapshot {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 股票代码 */
    private String symbol;

    /** 交易所代码 */
    private String exchangeCode;

    /** 交易日 */
    private String marketDay;

    /** 开盘价 */
    private BigDecimal openPrice;

    /** 最高价 */
    private BigDecimal highPrice;

    /** 最低价 */
    private BigDecimal lowPrice;

    /** 收盘价 */
    private BigDecimal closePrice;

    /** 成交量 */
    private Long volumeQty;

    /** 成交额 */
    private BigDecimal turnoverAmount;

    /** 同步标记：0-未同步，1-已同步 */
    private Integer syncFlag;
}
