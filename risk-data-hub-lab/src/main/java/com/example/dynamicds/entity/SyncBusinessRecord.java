package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

/**
 * 业务同步记录
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("sync_business_record")
public class SyncBusinessRecord {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long taskId;
    private String businessCode;
    private String status;
    private Integer pageCount;
    private Integer pulledCount;
    private Integer savedCount;
    private Long lastRowId;
    private String errorMessage;
    private String startedAt;
    private String finishedAt;
}
