package com.riskdatahub.controller;

import com.riskdatahub.common.result.ApiResult;
import com.riskdatahub.overview.OverviewService;
import com.riskdatahub.overview.dto.OverviewVO;
import com.riskdatahub.sync.SyncOrchestrator;
import com.riskdatahub.sync.entity.CleanTrade;
import com.riskdatahub.task.SyncTaskService;
import com.riskdatahub.task.entity.SyncTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 核心业务控制器 — 提供系统总览、同步任务、清洗记录查询等核心功能入口。
 * <p>
 * <b>RESTful 设计原则：</b>
 * <ul>
 *   <li>{@code GET} — 无副作用的查询操作（总览、任务状态、查询结果）</li>
 *   <li>{@code POST} — 有副作用的命令操作（提交同步）</li>
 * </ul>
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@RestController
@RequestMapping("/api/hub")
@RequiredArgsConstructor
public class HubController {

    private final OverviewService overviewService;
    private final SyncOrchestrator syncOrchestrator;
    private final SyncTaskService syncTaskService;

    /**
     * 获取系统总览数据。
     * <p>
     * 返回项目名称、摘要、系统拓扑、各类表统计、Leaf 发号器状态等信息。
     * </p>
     *
     * @return 系统总览数据
     */
    @GetMapping("/overview")
    public ApiResult<OverviewVO> overview() {
        return ApiResult.ok(overviewService.overview(), "OVERVIEW_LOADED");
    }

    /**
     * 提交异步同步任务。
     *
     * @param request 同步请求参数（数据源标识 + 分页大小）
     * @return 刚创建的同步任务
     */
    @PostMapping("/sync")
    public ApiResult<SyncTask> sync(@Valid @RequestBody SyncRequest request) {
        return ApiResult.ok(
                syncTaskService.startTask(request.getDataSourceKey(), request.getPageSize()),
                "SYNC_TASK_STARTED");
    }

    /**
     * 查询当前同步任务状态。
     *
     * @return 最近一条同步任务的状态
     */
    @GetMapping("/sync-task")
    public ApiResult<SyncTask> syncTask() {
        return ApiResult.ok(syncTaskService.currentTask(), "SYNC_TASK_STATUS");
    }

    /**
     * 查询最近 30 条清洗后的交易记录。
     *
     * @return 清洗交易记录列表
     */
    @GetMapping("/cleaned-trades")
    public ApiResult<List<CleanTrade>> cleanedTrades() {
        return ApiResult.ok(syncOrchestrator.cleanedTrades());
    }

    /**
     * 同步任务请求体 — 封装数据源 key 和分页大小。
     */
    @Data
    public static class SyncRequest {

        /** 数据源唯一标识 */
        @NotBlank(message = "数据源 key 不能为空")
        private String dataSourceKey;

        /** 每页记录数，范围 1~500 */
        @Min(value = 1, message = "分页大小最小为 1")
        @Max(value = 500, message = "分页大小最大为 500")
        private int pageSize = 100;
    }
}
