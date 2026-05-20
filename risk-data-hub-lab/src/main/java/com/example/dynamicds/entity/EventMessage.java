package com.example.dynamicds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("event_message")
@Data
public class EventMessage {
    @TableId(type = IdType.INPUT)
    private Long messageId;
    private String topic;
    private String bizKey;
    private String payload;
    private String status;
    private String createdAt;
}
