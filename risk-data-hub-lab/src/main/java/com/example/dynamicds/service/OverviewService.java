package com.example.dynamicds.service;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.dto.OverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 概览服务 — 提供系统总览数据，包括数据源拓扑、业务表统计、中台表统计、
 * Leaf 发号器状态等，供前端 /api/hub/overview 接口使用。
 */
@Service
@RequiredArgsConstructor
public class OverviewService {

    private final DynamicDataSourceManager manager;
    private final PlatformBootstrapService bootstrapService;
    private final LeafSegmentService leafSegmentService;
    private final OverviewBlueprint overviewBlueprint;

    public OverviewVO overview() {
        Map<String, Integer> hubStats = bootstrapService.currentHubTableStats();
        OverviewVO.LeafStateVO leafState = overviewBlueprint.toLeafState(leafSegmentService.getState("clean_trade"));

        return OverviewVO.builder()
                .project(overviewBlueprint.projectName())
                .summary(overviewBlueprint.projectSummary())
                .topology(bootstrapService.currentTopology())
                .businessTableStats(bootstrapService.currentBusinessTableStats())
                .hubTableStats(hubStats)
                .datasourceCount(manager.keys().size())
                .cleanTradeCount(hubStats.getOrDefault("clean_trade", 0))
                .eventCount(hubStats.getOrDefault("event_message", 0))
                .architectureAnswers(overviewBlueprint.architectureAnswers())
                .leafState(leafState)
                .build();
    }
}
