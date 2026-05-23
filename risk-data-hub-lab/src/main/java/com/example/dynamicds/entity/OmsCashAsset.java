package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("oms_cash_asset")
public class OmsCashAsset {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String investorName;
    private String accountNo;
    private BigDecimal cashBalance;
    private BigDecimal frozenBalance;
    private BigDecimal totalAsset;
    private String statDay;
    private Integer syncFlag;
}
