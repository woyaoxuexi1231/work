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
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 同步数据查询控制器 — 任务状态、清洗记录、业务明细、批次耗时。
 *
 * @author risk-data-hub
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SyncQueryController {

    private final SyncTaskService syncTaskService;

    /**
     * 查询当前同步任务状态。
     */
    @PostMapping("/api-hub-sync-task")
    public ApiResult<SyncTask> syncTask() {
        return ApiResult.ok(syncTaskService.currentTask(), "SYNC_TASK_STATUS");
    }

    /**
     * 查询最近 30 条清洗后的交易记录。
     */
    @PostMapping("/api-hub-cleaned-trades")
    public ApiResult<List<CleanTrade>> cleanedTrades() {
        return ApiResult.ok(syncTaskService.cleanedTrades());
    }

    /**
     * 查询指定同步任务的各业务执行详情。
     */
    @PostMapping("/api-hub-sync-detail")
    public ApiResult<List<SyncBusinessRecord>> syncDetail(@Valid @RequestBody DetailRequest request) {
        return ApiResult.ok(
                syncTaskService.getBusinessRecords(request.getTaskId()),
                "SYNC_BUSINESS_DETAIL");
    }

    /**
     * 查询指定业务记录的批次耗时明细。
     */
    @PostMapping("/api-hub-batch-metrics")
    public ApiResult<IPage<SyncBatchMetrics>> batchMetrics(@Valid @RequestBody BatchMetricsRequest request) {
        return ApiResult.ok(
                syncTaskService.getBatchMetrics(request.getRecordId(), request.getPage(), request.getSize()),
                "SYNC_BATCH_METRICS");
    }

    @Data
    public static class DetailRequest {
        @NotNull(message = "任务 ID 不能为空")
        private Long taskId;
    }

    @Data
    public static class BatchMetricsRequest {
        @NotNull(message = "记录 ID 不能为空")
        private Long recordId;
        private int page = 1;
        @Min(1) @Max(200)
        private int size = 50;
    }
}
