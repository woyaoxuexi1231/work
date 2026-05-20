package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@TableName("raw_trade")
@Data
public class RawTrade {
    @TableId
    private Long id;
    private String vendorTradeNo;
    private String bizType;
    private String directionCode;
    private BigDecimal amount;
    private String statusCode;
    private String counterpartyCode;
    private String tradeTime;
    private Integer cleaned;
}
