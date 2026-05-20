package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("broker_trade_deal")
public class BrokerTradeDeal {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String dealCode;
    private String secuCode;
    private String clientFullName;
    private String bsFlag;
    private Long dealVolume;
    private BigDecimal dealPrice;
    private BigDecimal turnoverAmount;
    private String statusMark;
    private String dealAt;
    private Integer syncFlag;
}
