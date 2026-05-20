package com.example.dynamicds.controller;

import com.example.dynamicds.dto.ApiResult;
import com.example.dynamicds.dto.SyncRequest;
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

import java.util.Map;

/**
 * 核心业务控制器 — 提供中台总览、初始化、同步触发、查询接口。
 * <p>
 * <b>接口风格：全部 POST + 请求体对象</b>，无路径参数、无 Query 参数。
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
    public ApiResult<Map<String, Object>> overview() {
        log.info("[控制层] 查询项目总览");
        return ApiResult.ok(overviewService.overview());
    }

    @PostMapping("/init-data")
    public ApiResult<Map<String, Object>> initData() {
        log.info("[控制层] 提交异步初始化任务");
        try {
            return ApiResult.ok(initDataTaskService.startTask());
        } catch (IllegalStateException e) {
            return ApiResult.fail(409, e.getMessage());
        }
    }

    @PostMapping("/init-task")
    public ApiResult<Map<String, Object>> initTask() {
        return ApiResult.ok(initDataTaskService.currentTask());
    }

    @PostMapping("/sync")
    public ApiResult<Map<String, Object>> sync(@RequestBody SyncRequest request) {
        log.info("[控制层] 提交异步同步任务 dataSourceKey={}, pageSize={}", request.getDataSourceKey(), request.getPageSize());
        try {
            return ApiResult.ok(syncTaskService.startTask(request.getDataSourceKey(), request.getPageSize()));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResult.fail(409, e.getMessage());
        }
    }

    @PostMapping("/sync-task")
    public ApiResult<Map<String, Object>> syncTask() {
        return ApiResult.ok(syncTaskService.currentTask());
    }

    @PostMapping("/cleaned-trades")
    public ApiResult<?> cleanedTrades() {
        return ApiResult.ok(tradeEtlService.cleanedTrades());
    }
}
