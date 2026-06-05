package com.riskdatahub.sync;

import com.riskdatahub.common.result.ApiResult;
import com.riskdatahub.sync.entity.CleanTrade;
import com.riskdatahub.task.SyncTaskService;
import com.riskdatahub.task.entity.SyncBusinessRecord;
import com.riskdatahub.task.entity.SyncTask;
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
 * 同步任务控制器 — 提供同步任务提交、状态查询和清洗记录查看功能。
 *
 * @author risk-data-hub
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SyncController {

    private final SyncOrchestrator syncOrchestrator;
    private final SyncTaskService syncTaskService;

    /**
     * 提交异步同步任务。
     *
     * @param request 同步请求参数（数据源标识 + 分页大小）
     * @return 刚创建的同步任务
     */
    @PostMapping("/api-hub-sync")
    public ApiResult<SyncTask> sync(@Valid @RequestBody SyncRequest request) {
        return ApiResult.ok(
                syncTaskService.startTask(request.getDataSourceKey(), request.getPageSize()),
                "SYNC_TASK_STARTED");
    }

    /**
     * 强制刷新 — 清除 risk_hub 全部业务数据，然后重新全量同步。
     *
     * @param request 同步请求参数（数据源标识 + 分页大小）
     * @return 刚创建的同步任务
     */
    @PostMapping("/api-hub-sync-force-refresh")
    public ApiResult<SyncTask> forceRefresh(@Valid @RequestBody SyncRequest request) {
        return ApiResult.ok(
                syncTaskService.forceRefresh(request.getDataSourceKey(), request.getPageSize()),
                "FORCE_REFRESH_STARTED");
    }

    /**
     * 查询当前同步任务状态。
     *
     * @return 最近一条同步任务的状态
     */
    @PostMapping("/api-hub-sync-task")
    public ApiResult<SyncTask> syncTask() {
        return ApiResult.ok(syncTaskService.currentTask(), "SYNC_TASK_STATUS");
    }

    /**
     * 查询最近 30 条清洗后的交易记录。
     *
     * @return 清洗交易记录列表
     */
    @PostMapping("/api-hub-cleaned-trades")
    public ApiResult<List<CleanTrade>> cleanedTrades() {
        return ApiResult.ok(syncOrchestrator.cleanedTrades());
    }

    /**
     * 查询指定同步任务的各业务执行详情。
     *
     * @param request 包含 taskId 的请求体
     * @return 业务执行记录列表（按 businessCode 排序）
     */
    @PostMapping("/api-hub-sync-detail")
    public ApiResult<List<SyncBusinessRecord>> syncDetail(@Valid @RequestBody DetailRequest request) {
        return ApiResult.ok(
                syncTaskService.getBusinessRecords(request.getTaskId()),
                "SYNC_BUSINESS_DETAIL");
    }

    /**
     * 同步业务详情请求体。
     */
    @Data
    public static class DetailRequest {
        @NotNull(message = "任务 ID 不能为空")
        private Long taskId;
    }

    /**
     * 同步任务请求体 — 封装数据源 key 和分页大小。
     */
    @Data
    public static class SyncRequest {

        /** 数据源唯一标识 */
        @NotBlank(message = "数据源 key 不能为空")
        private String dataSourceKey;

        /** 每页记录数，默认 10000，最大 100000 */
        @Min(value = 1, message = "分页大小最小为 1")
        @Max(value = 100000, message = "分页大小最大为 100000")
        private int pageSize = 10000;
    }
}
