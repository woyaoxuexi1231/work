package com.riskdatahub.overview;

import com.riskdatahub.overview.dto.OverviewVO.LeafStateVO;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 概览信息供应者 — 提供项目描述、架构说明等静态展示信息。
 *
 * @author risk-data-hub
 */
@Component
public class OverviewInfoSupplier {

    private static final String PROJECT_NAME = "精简版数据中台同步实验室";
    private static final String PROJECT_SUMMARY = "启动阶段只保证表结构正常。演示数据由前端手动触发初始化；同步阶段按模板模式并发处理股票、交易、持仓、资金四类业务。";
    private static final List<String> ARCHITECTURE_ANSWERS = Arrays.asList(
            "动态数据源只保留最核心的维护能力：查看、注册、删除，并在注册时带上 datasourceType。",
            "启动阶段只做 schema 和 table 校验，不再自动灌演示数据；演示数据初始化改成前端手动触发。",
            "同步流程改成模板模式，固定三步：拉取数据 -> 转换数据 -> 落库。",
            "每种业务内部固定一对线程：一个拉取线程，一个落库线程；不同业务类型之间并发执行。",
            "当前同步覆盖 4 类业务：股票、交易、持仓、资金，便于你后面做多表并发和多线程验证。"
    );

    /**
     * 获取项目名称。
     *
     * @return 项目名称
     */
    public String projectName() {
        return PROJECT_NAME;
    }

    /**
     * 获取项目摘要。
     *
     * @return 项目摘要
     */
    public String projectSummary() {
        return PROJECT_SUMMARY;
    }

    /**
     * 获取架构设计说明列表。
     *
     * @return 架构说明列表
     */
    public List<String> architectureAnswers() {
        return ARCHITECTURE_ANSWERS;
    }

    /**
     * 将 Leaf 发号器状态 Map 转换为视图对象。
     *
     * @param leafStateMap Leaf 发号器原始状态
     * @return Leaf 状态视图对象
     */
    public LeafStateVO toLeafState(Map<String, Object> leafStateMap) {
        return LeafStateVO.builder()
                .currentStart(longValue(leafStateMap.get("currentStart")))
                .currentNext(longValue(leafStateMap.get("currentNext")))
                .step(stringValue(leafStateMap.get("step")))
                .mode(stringValue(leafStateMap.get("mode")))
                .description(stringValue(leafStateMap.get("description")))
                .build();
    }

    private Long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
