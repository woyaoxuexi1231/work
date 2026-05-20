package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 初始化任务记录
 */
@Data
@TableName("init_task")
public class InitTask {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String status;
    private String submittedAt;
    private String startedAt;
    private String finishedAt;
    private Integer progress;
    private String message;
    private String errorMessage;
    @JsonIgnore
    private String result;
    /** 任务是否正在运行 — 由 status 计算，非数据库字段 */
    @TableField(exist = false)
    private boolean running;
}
