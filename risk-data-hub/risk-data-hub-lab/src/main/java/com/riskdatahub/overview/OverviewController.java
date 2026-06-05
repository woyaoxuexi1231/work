package com.riskdatahub.overview;

import com.riskdatahub.common.result.ApiResult;
import com.riskdatahub.overview.dto.OverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统总览控制器 — 提供中台整体概况查询。
 *
 * @author risk-data-hub
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overviewService;

    /**
     * 获取系统总览数据。
     * <p>
     * 返回项目名称、摘要、系统拓扑、各类表统计、Leaf 发号器状态等信息。
     * </p>
     *
     * @return 系统总览数据
     */
    @PostMapping("/api-hub-overview")
    public ApiResult<OverviewVO> overview() {
        return ApiResult.ok(overviewService.overview(), "OVERVIEW_LOADED");
    }
}
