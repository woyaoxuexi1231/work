package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 初始化任务记录 — 替代原内存中的 InitTaskSnapshot。
 */
@Data
@TableName("init_task")
public class InitTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String status;
    private String submittedAt;
    private String startedAt;
    private String finishedAt;
    private String message;
    private String errorMessage;
    private String result;
}
