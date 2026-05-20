package com.example.dynamicds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 初始化任务响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitTaskVO {
    private String taskId;
    private String status;
    private int progress;
    private String submittedAt;
    private String startedAt;
    private String finishedAt;
    private String message;
    private String errorMessage;
    private boolean running;
}
