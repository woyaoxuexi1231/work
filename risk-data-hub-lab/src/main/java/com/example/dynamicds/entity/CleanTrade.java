package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.dynamicds.sync.CleanRecordContext;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@TableName("clean_trade")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CleanTrade {
    @TableId(type = IdType.INPUT)
    private Long globalId;
    private String sourceSystem;
    private String sourceType;
    private Long sourceRowId;
    private String vendorTradeNo;
    private String bizType;
    private String direction;
    private BigDecimal amount;
    private String statusName;
    private String counterpartyName;
    private String cleanMode;
    private String cleanBatch;
    private String tradeTime;
    private String createdAt;

    public static CleanTrade create(CleanRecordContext context,
                                    String vendorTradeNo,
                                    String bizType,
                                    String direction,
                                    BigDecimal amount,
                                    String statusName,
                                    String counterpartyName,
                                    String cleanMode,
                                    String tradeTime) {
        CleanTrade trade = new CleanTrade();
        trade.setGlobalId(context.globalId());
        trade.setSourceSystem(context.sourceSystem());
        trade.setSourceType(context.sourceType());
        trade.setSourceRowId(context.sourceRowId());
        trade.setVendorTradeNo(vendorTradeNo);
        trade.setBizType(bizType);
        trade.setDirection(direction);
        trade.setAmount(amount);
        trade.setStatusName(statusName);
        trade.setCounterpartyName(counterpartyName);
        trade.setCleanMode(cleanMode);
        trade.setCleanBatch(context.cleanBatch());
        trade.setTradeTime(tradeTime);
        trade.setCreatedAt(context.createdAt());
        return trade;
    }
}
