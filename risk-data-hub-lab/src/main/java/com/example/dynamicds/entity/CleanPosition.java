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

@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("clean_position")
public class CleanPosition {
    @TableId(type = IdType.INPUT)
    private Long globalId;
    private String sourceSystem;
    private String sourceType;
    private Long sourceRowId;
    private String accountName;
    private String stockCode;
    private Long holdingQty;
    private Long availableQty;
    private BigDecimal costPrice;
    private BigDecimal marketValue;
    private String statDay;
    private String cleanBatch;
    private String createdAt;

    public static CleanPosition create(CleanRecordContext context,
                                       String accountName,
                                       String stockCode,
                                       Long holdingQty,
                                       Long availableQty,
                                       BigDecimal costPrice,
                                       BigDecimal marketValue,
                                       String statDay) {
        CleanPosition position = new CleanPosition();
        position.setGlobalId(context.globalId());
        position.setSourceSystem(context.sourceSystem());
        position.setSourceType(context.sourceType());
        position.setSourceRowId(context.sourceRowId());
        position.setAccountName(accountName);
        position.setStockCode(stockCode);
        position.setHoldingQty(holdingQty);
        position.setAvailableQty(availableQty);
        position.setCostPrice(costPrice);
        position.setMarketValue(marketValue);
        position.setStatDay(statDay);
        position.setCleanBatch(context.cleanBatch());
        position.setCreatedAt(context.createdAt());
        return position;
    }
}
