package com.example.mybatis.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日志实体 - 用于演示自定义Executor
 */
@Data
@TableName("t_log")
public class Log {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 日志级别：INFO, WARN, ERROR
     */
    private String level;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 操作IP
     */
    private String ip;

    private LocalDateTime createTime;
}
