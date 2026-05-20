package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("tx_coordination_log")
@Data
public class TxCoordinationLog {
    @TableId
    private Long id;
    private String sourceSystem;
    private String phase;
    private String detail;
    private String createdAt;
}
