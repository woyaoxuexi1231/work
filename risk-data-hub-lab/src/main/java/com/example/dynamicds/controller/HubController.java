package com.example.dynamicds.controller;

import com.example.dynamicds.dto.ApiResult;
import com.example.dynamicds.dto.OverviewVO;
import com.example.dynamicds.dto.SyncRequest;
import com.example.dynamicds.entity.InitTask;
import com.example.dynamicds.entity.SyncTask;
import com.example.dynamicds.service.InitDataTaskService;
import com.example.dynamicds.service.OverviewService;
import com.example.dynamicds.service.SyncTaskService;
import com.example.dynamicds.service.TradeEtlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 核心业务控制器 — 全部 POST + 请求体对象。
 */
@RestController
@RequestMapping("/api/hub")
@Slf4j
@RequiredArgsConstructor
public class HubController {

    private final OverviewService overviewService;
    private final InitDataTaskService initDataTaskService;
    private final TradeEtlService tradeEtlService;
    private final SyncTaskService syncTaskService;

    @PostMapping("/overview")
    public ApiResult<OverviewVO> overview() {
        log.info("[控制层] 查询项目总览");
        return ApiResult.ok(overviewService.overview(), "OVERVIEW_LOADED");
    }

    @PostMapping("/init-data")
    public ApiResult<InitTask> initData() {
        log.info("[控制层] 提交异步初始化任务");
        try {
            return ApiResult.ok(initDataTaskService.startTask(), "INIT_TASK_STARTED");
        } catch (IllegalStateException e) {
            return ApiResult.getFail(409, e.getMessage(), "INIT_TASK_ALREADY_RUNNING");
        }
    }

    @PostMapping("/init-task")
    public ApiResult<InitTask> initTask() {
        return ApiResult.ok(initDataTaskService.currentTask(), "INIT_TASK_STATUS");
    }

    @PostMapping("/sync")
    public ApiResult<SyncTask> sync(@RequestBody SyncRequest request) {
        log.info("[控制层] 提交异步同步任务 dataSourceKey={}, pageSize={}", request.getDataSourceKey(), request.getPageSize());
        try {
            return ApiResult.ok(syncTaskService.startTask(request.getDataSourceKey(), request.getPageSize()), "SYNC_TASK_STARTED");
        } catch (IllegalArgumentException e) {
            return ApiResult.getFail(400, e.getMessage(), "SYNC_TASK_INVALID_PARAM");
        } catch (IllegalStateException e) {
            return ApiResult.getFail(409, e.getMessage(), "SYNC_TASK_ALREADY_RUNNING");
        }
    }

    @PostMapping("/sync-task")
    public ApiResult<SyncTask> syncTask() {
        return ApiResult.ok(syncTaskService.currentTask(), "SYNC_TASK_STATUS");
    }

    @PostMapping("/cleaned-trades")
    public ApiResult<List<?>> cleanedTrades() {
        return ApiResult.ok(tradeEtlService.cleanedTrades());
    }
}
