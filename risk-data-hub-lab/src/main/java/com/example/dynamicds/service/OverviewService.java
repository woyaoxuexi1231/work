package com.example.dynamicds.service;

import com.example.dynamicds.datasource.RoutingMybatisExecutor;
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

    public OverviewVO overview() {
        Map<String, Integer> hubStats = bootstrapService.currentHubTableStats();
        Map<String, Object> leafStateMap = leafSegmentService.state("clean_trade");

        OverviewVO.LeafStateVO leafState = OverviewVO.LeafStateVO.builder()
                .currentStart(leafStateMap.get("currentStart") != null ? ((Number) leafStateMap.get("currentStart")).longValue() : null)
                .currentNext(leafStateMap.get("currentNext") != null ? ((Number) leafStateMap.get("currentNext")).longValue() : null)
                .step(leafStateMap.get("step") != null ? leafStateMap.get("step").toString() : null)
                .mode(leafStateMap.get("mode") != null ? leafStateMap.get("mode").toString() : null)
                .description(leafStateMap.get("description") != null ? leafStateMap.get("description").toString() : null)
                .build();

        return OverviewVO.builder()
                .project("精简版数据中台同步实验室")
                .summary("启动阶段只保证表结构正常。演示数据由前端手动触发初始化；同步阶段按模板模式并发处理股票、交易、持仓、资金四类业务。")
                .topology(bootstrapService.currentTopology())
                .businessTableStats(bootstrapService.currentBusinessTableStats())
                .hubTableStats(hubStats)
                .datasourceCount(manager.keys().size())
                .cleanTradeCount(hubStats.getOrDefault("clean_trade", 0))
                .eventCount(hubStats.getOrDefault("event_message", 0))
                .architectureAnswers(List.of(
                        "动态数据源只保留最核心的维护能力：查看、注册、删除，并在注册时带上 datasourceType。",
                        "启动阶段只做 schema 和 table 校验，不再自动灌演示数据；演示数据初始化改成前端手动触发。",
                        "同步流程改成模板模式，固定三步：拉取数据 -> 转换数据 -> 落库。",
                        "每种业务内部固定一对线程：一个拉取线程，一个落库线程；不同业务类型之间并发执行。",
                        "当前同步覆盖 4 类业务：股票、交易、持仓、资金，便于你后面做多表并发和多线程验证。"
                ))
                .leafState(leafState)
                .build();
    }
}
