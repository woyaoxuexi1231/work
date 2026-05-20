package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("broker_position_balance")
public class BrokerPositionBalance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clientFullName;
    private String secuCode;
    private Long currentVolume;
    private Long enableVolume;
    private BigDecimal costPx;
    private BigDecimal marketAmt;
    private String bizDate;
    private Integer syncFlag;
}
