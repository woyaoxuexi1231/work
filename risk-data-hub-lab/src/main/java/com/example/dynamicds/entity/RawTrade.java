package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 原始交易记录实体（当前未使用 — 保留供参考）。
 * 注意：raw_trade 表不在任何 DDL 创建逻辑中，该实体未被任何业务逻辑引用。
 * 若后续需要引入原始交易层，请先在 DDL 中创建 raw_trade 表并实现对应 Mapper。
 */
@Deprecated
@TableName("raw_trade")
@Getter
@Setter
@ToString
@NoArgsConstructor
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
