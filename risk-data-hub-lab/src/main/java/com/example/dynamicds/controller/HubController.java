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

/**
 * 核心业务控制器 — 提供中台总览、初始化、同步触发、查询接口。
 * 所有业务操作通过异步任务执行，前端轮询任务状态。
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

    /**
     * GET /api/hub/overview — 查询系统总览：拓扑、各表统计、发号器状态
     */
    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        log.info("[控制层] 查询项目总览");
        return ApiResult.ok(overviewService.overview());
    }

    /**
     * POST /api/hub/init-data — 触发异步初始化（清空旧数据 → 重新灌入演示数据）
     */
    @PostMapping("/init-data")
    public ApiResult<Map<String, Object>> initData() {
        log.info("[控制层] 提交异步初始化任务");
        try {
            return ApiResult.ok(initDataTaskService.startTask());
        } catch (IllegalStateException e) {
            return ApiResult.fail(409, e.getMessage());
        }
    }

    /**
     * GET /api/hub/init-task — 查询当前初始化任务状态（供前端轮询）
     */
    @GetMapping("/init-task")
    public ApiResult<Map<String, Object>> initTask() {
        return ApiResult.ok(initDataTaskService.currentTask());
    }

    /**
     * POST /api/hub/sync — 触发异步同步，对指定数据源执行 4 类业务并发同步
     */
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

    /**
     * GET /api/hub/sync-task — 查询当前同步任务状态（供前端轮询）
     */
    @GetMapping("/sync-task")
    public ApiResult<Map<String, Object>> syncTask() {
        return ApiResult.ok(syncTaskService.currentTask());
    }

    /**
     * GET /api/hub/cleaned-trades — 查询中台库最近 30 条标准化交易记录
     */
    @GetMapping("/cleaned-trades")
    public ApiResult<?> cleanedTrades() {
        return ApiResult.ok(tradeEtlService.cleanedTrades());
    }
}
