package com.example.dynamicds.controller;

import com.example.dynamicds.dto.ApiResult;
import com.example.dynamicds.service.InitDataTaskService;
import com.example.dynamicds.service.OverviewService;
import com.example.dynamicds.service.PlatformBootstrapService;
import com.example.dynamicds.service.SyncTaskService;
import com.example.dynamicds.service.TradeEtlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/hub")
@Slf4j
@RequiredArgsConstructor
public class HubController {

    private final OverviewService overviewService;
    private final InitDataTaskService initDataTaskService;
    private final TradeEtlService tradeEtlService;
    private final SyncTaskService syncTaskService;

    @GetMapping("/overview")
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

    @GetMapping("/init-task")
    public ApiResult<Map<String, Object>> initTask() {
        return ApiResult.ok(initDataTaskService.currentTask());
    }

    @PostMapping("/sync")
    public ApiResult<Map<String, Object>> sync(@RequestParam String dataSourceKey,
                                               @RequestParam(defaultValue = "2") int pageSize) {
        log.info("[控制层] 提交异步同步任务 dataSourceKey={}, pageSize={}", dataSourceKey, pageSize);
        try {
            return ApiResult.ok(syncTaskService.startTask(dataSourceKey, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResult.fail(409, e.getMessage());
        }
    }

    @GetMapping("/sync-task")
    public ApiResult<Map<String, Object>> syncTask() {
        return ApiResult.ok(syncTaskService.currentTask());
    }

    @GetMapping("/cleaned-trades")
    public ApiResult<?> cleanedTrades() {
        return ApiResult.ok(tradeEtlService.cleanedTrades());
    }
}
