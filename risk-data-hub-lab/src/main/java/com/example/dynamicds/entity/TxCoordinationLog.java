package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@TableName("tx_coordination_log")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class TxCoordinationLog {
    @TableId
    private Long id;
    private String sourceSystem;
    private String phase;
    private String detail;
    private String createdAt;
}
