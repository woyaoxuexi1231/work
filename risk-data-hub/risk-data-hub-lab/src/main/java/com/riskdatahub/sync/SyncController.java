package com.riskdatahub.sync;

import com.riskdatahub.common.result.ApiResult;
import com.riskdatahub.sync.task.SyncTaskService;
import com.riskdatahub.sync.task.entity.SyncTask;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * 同步任务控制器 — 提交增量同步、全量同步。
 *
 * @author risk-data-hub
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SyncController {

    private final SyncTaskService syncTaskService;

    /** 提交增量同步任务。 */
    @PostMapping("/api-hub-sync")
    public ApiResult<SyncTask> sync(@Valid @RequestBody SyncRequest request) {
        return ApiResult.ok(
                syncTaskService.startTask(request.getDataSourceKey(), request.getPageSize()),
                "SYNC_TASK_STARTED");
    }

    /** 提交全量同步任务（从头开始，不断点续传）。 */
    @PostMapping("/api-hub-sync-full")
    public ApiResult<SyncTask> fullSync(@Valid @RequestBody SyncRequest request) {
        return ApiResult.ok(
                syncTaskService.fullSync(request.getDataSourceKey(), request.getPageSize()),
                "FULL_SYNC_STARTED");
    }

    @Data
    public static class SyncRequest {
        @NotBlank(message = "数据源 key 不能为空")
        private String dataSourceKey;

        @Min(value = 1, message = "分页大小最小为 1")
        @Max(value = 100000, message = "分页大小最大为 100000")
        private int pageSize = 10000;
    }
}
