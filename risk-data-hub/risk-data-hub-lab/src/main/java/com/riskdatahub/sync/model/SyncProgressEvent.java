package com.riskdatahub.sync.model;

import lombok.Data;

/**
 * 同步进度事件 — 每个业务每完成一页拉取或落库时发布。
 */
@Data
public class SyncProgressEvent {

    private final Long taskId;
    private final String businessCode;
    private final int pulledCount;
    private final int savedCount;

    public SyncProgressEvent(Long taskId, String businessCode, int pulledCount, int savedCount) {
        this.taskId = taskId;
        this.businessCode = businessCode;
        this.pulledCount = pulledCount;
        this.savedCount = savedCount;
    }
}
