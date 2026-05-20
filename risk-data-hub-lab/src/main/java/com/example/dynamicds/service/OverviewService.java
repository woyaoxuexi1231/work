package com.example.dynamicds.service;

import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> hubStats = bootstrapService.currentHubTableStats();
        result.put("project", "精简版数据中台同步实验室");
        result.put("summary", "启动阶段只保证表结构正常。演示数据由前端手动触发初始化；同步阶段按模板模式并发处理股票、交易、持仓、资金四类业务。");
        result.put("topology", bootstrapService.currentTopology());
        result.put("businessTableStats", bootstrapService.currentBusinessTableStats());
        result.put("hubTableStats", hubStats);
        result.put("datasourceCount", manager.keys().size());
        result.put("cleanTradeCount", hubStats.getOrDefault("clean_trade", 0));
        result.put("eventCount", hubStats.getOrDefault("event_message", 0));
        result.put("architectureAnswers", List.of(
                "动态数据源只保留最核心的维护能力：查看、注册、删除，并在注册时带上 datasourceType。",
                "启动阶段只做 schema 和 table 校验，不再自动灌演示数据；演示数据初始化改成前端手动触发。",
                "同步流程改成模板模式，固定三步：拉取数据 -> 转换数据 -> 落库。",
                "每种业务内部固定一对线程：一个拉取线程，一个落库线程；不同业务类型之间并发执行。",
                "当前同步覆盖 4 类业务：股票、交易、持仓、资金，便于你后面做多表并发和多线程验证。"
        ));
        result.put("leafState", leafSegmentService.state("clean_trade"));
        return result;
    }
}
