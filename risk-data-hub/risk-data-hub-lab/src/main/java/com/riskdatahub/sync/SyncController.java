package com.riskdatahub.sync;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.riskdatahub.common.result.ApiResult;
import com.riskdatahub.sync.entity.CleanTrade;
import com.riskdatahub.sync.entity.SyncBatchMetrics;
import com.riskdatahub.sync.task.SyncTaskService;
import com.riskdatahub.sync.task.entity.SyncBusinessRecord;
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
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 同步任务控制器 — 提交同步任务、强制刷新。
 *
 * @author risk-data-hub
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SyncController {

    private final SyncTaskService syncTaskService;

    /**
     * 提交异步同步任务。
     */
    @PostMapping("/api-hub-sync")
    public ApiResult<SyncTask> sync(@Valid @RequestBody SyncRequest request) {
        return ApiResult.ok(
                syncTaskService.startTask(request.getDataSourceKey(), request.getPageSize()),
                "SYNC_TASK_STARTED");
    }

    /**
     * 强制刷新 — 清除 risk_hub 全部业务数据，然后重新全量同步。
     */
    @PostMapping("/api-hub-sync-force-refresh")
    public ApiResult<SyncTask> forceRefresh(@Valid @RequestBody SyncRequest request) {
        return ApiResult.ok(
                syncTaskService.forceRefresh(request.getDataSourceKey(), request.getPageSize()),
                "FORCE_REFRESH_STARTED");
    }

    /**
     * 同步任务请求体。
     */
    @Data
    public static class SyncRequest {
        @NotBlank(message = "数据源 key 不能为空")
        private String dataSourceKey;

        @Min(value = 1, message = "分页大小最小为 1")
        @Max(value = 100000, message = "分页大小最大为 100000")
        private int pageSize = 10000;
    }
}
