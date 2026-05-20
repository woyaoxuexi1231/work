package com.mlm.pipeline.engine;

import com.mlm.common.enums.EpisodeStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 步骤处理器注册表 — Spring 自动收集所有 StepHandler Bean
 * <p>
 * 按 step() 返回值建立 EpisodeStatus → StepHandler 的映射。
 */
@Component
public class StepHandlerRegistry {

    private final Map<EpisodeStatus, StepHandler> registry;

    public StepHandlerRegistry(List<StepHandler> handlers) {
        this.registry = handlers.stream()
            .collect(Collectors.toMap(StepHandler::step, Function.identity()));
    }

    /**
     * 获取指定状态对应的处理器
     *
     * @param status 剧集状态
     * @return 步骤处理器
     */
    public StepHandler get(EpisodeStatus status) {
        StepHandler handler = registry.get(status);
        if (handler == null) {
            throw new IllegalArgumentException("未找到步骤处理器: " + status);
        }
        return handler;
    }
}
