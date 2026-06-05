package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.riskdatahub.sync.model.CleanRecordContext;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 中台清洗后股票行情实体。
 * <p>
 * 对应数据库表 {@code clean_stock}，存储经 ETL 清洗后的标准化股票行情数据。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("clean_stock")
public class CleanStock {

    @TableId(type = IdType.INPUT)
    private Long globalId;

    /** 来源系统标识 */
    private String sourceSystem;

    /** 来源系统类型 */
    private String sourceType;

    /** 来源系统记录 ID */
    private Long sourceRowId;

    /** 股票代码 */
    private String stockCode;

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

    /** 清洗批次号 */
    private String cleanBatch;

    /** 记录创建时间 */
    private String createdAt;

    /**
     * 工厂方法 — 从清洗上下文和源数据构造中台实体。
     *
     * @param context      清洗记录上下文
     * @param stockCode    股票代码
     * @param exchangeCode 交易所代码
     * @param marketDay    交易日
     * @param openPrice    开盘价
     * @param highPrice    最高价
     * @param lowPrice     最低价
     * @param closePrice   收盘价
     * @param volumeQty    成交量
     * @param turnoverAmount 成交额
     * @return 中台股票实体
     */
    public static CleanStock create(CleanRecordContext context,
                                    String stockCode,
                                    String exchangeCode,
                                    String marketDay,
                                    BigDecimal openPrice,
                                    BigDecimal highPrice,
                                    BigDecimal lowPrice,
                                    BigDecimal closePrice,
                                    Long volumeQty,
                                    BigDecimal turnoverAmount) {
        CleanStock stock = new CleanStock();
        stock.setGlobalId(context.getGlobalId());
        stock.setSourceSystem(context.getSourceSystem());
        stock.setSourceType(context.getSourceType());
        stock.setSourceRowId(context.getSourceRowId());
        stock.setStockCode(stockCode);
        stock.setExchangeCode(exchangeCode);
        stock.setMarketDay(marketDay);
        stock.setOpenPrice(openPrice);
        stock.setHighPrice(highPrice);
        stock.setLowPrice(lowPrice);
        stock.setClosePrice(closePrice);
        stock.setVolumeQty(volumeQty);
        stock.setTurnoverAmount(turnoverAmount);
        stock.setCleanBatch(context.getCleanBatch());
        stock.setCreatedAt(context.getCreatedAt());
        return stock;
    }
}
