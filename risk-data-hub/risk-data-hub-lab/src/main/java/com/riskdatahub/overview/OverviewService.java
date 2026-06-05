package com.riskdatahub.overview;

import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.overview.dto.OverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 概览服务 — 提供系统总览数据，包括数据源拓扑、业务表统计、中台表统计、
 * Leaf 发号器状态等，供前端 {@code /api/hub/overview} 接口使用。
 *
 * @author risk-data-hub
 */
@Service
@RequiredArgsConstructor
public class OverviewService {

    private final DataSourceManager dataSourceManager;
    private final PlatformInfoService platformInfoService;
    private final LeafSegmentService leafSegmentService;
    private final OverviewInfoSupplier overviewInfoSupplier;

    /**
     * 生成系统总览数据。
     *
     * @return 系统总览 VO
     */
    public OverviewVO overview() {
        Map<String, Integer> hubStats = platformInfoService.currentHubTableStats();
        OverviewVO.LeafStateVO leafState = overviewInfoSupplier.toLeafState(
                leafSegmentService.state("clean_trade"));

        return OverviewVO.builder()
                .project(overviewInfoSupplier.projectName())
                .summary(overviewInfoSupplier.projectSummary())
                .topology(platformInfoService.currentTopology())
                .businessTableStats(platformInfoService.currentBusinessTableStats())
                .hubTableStats(hubStats)
                .datasourceCount(dataSourceManager.keys().size())
                .cleanTradeCount(hubStats.getOrDefault("clean_trade", 0))
                .eventCount(hubStats.getOrDefault("event_message", 0))
                .architectureAnswers(overviewInfoSupplier.architectureAnswers())
                .leafState(leafState)
                .build();
    }
}
