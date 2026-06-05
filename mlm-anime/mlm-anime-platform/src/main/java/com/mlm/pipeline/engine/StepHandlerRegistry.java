package com.mlm.pipeline.engine;

import com.mlm.common.enums.EpisodeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 步骤处理器注册表 — Spring 自动收集所有 StepHandler Bean
 * <p>
 * 实现原理：Spring 注入所有 {@link StepHandler} 实现类列表，
 * 按 {@link StepHandler#step()} 的返回值建立 {@code EpisodeStatus → Handler} 映射。
 * <p>
 * 【设计说明】
 * <ul>
 *   <li>新增步骤处理器只需实现 StepHandler 接口 + @Component，无需修改此注册表</li>
 *   <li>注册表初始化时若发现重复 step() 值会抛出异常，避免运行时歧义</li>
 *   <li>获取未注册的处理器时抛出 {@link IllegalArgumentException}</li>
 * </ul>
 *
 * @author mlm
 * @see StepHandler
 */
@Component
public class StepHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(StepHandlerRegistry.class);

    /** EpisodeStatus → StepHandler 映射表 */
    private final Map<EpisodeStatus, StepHandler> registry;

    /**
     * 构造注册表，自动收集所有 StepHandler Bean
     *
     * @param handlers Spring 自动注入的所有 StepHandler 实现
     * @throws IllegalArgumentException 存在重复的 step() 值时抛出
     */
    public StepHandlerRegistry(List<StepHandler> handlers) {
        this.registry = handlers.stream()
                .collect(Collectors.toMap(
                        StepHandler::step,
                        handler -> handler,
                        (existing, replacement) -> {
                            throw new IllegalArgumentException(
                                    "重复的步骤处理器: " + existing.step()
                                            + " (已有=" + existing.getClass().getSimpleName()
                                            + ", 新=" + replacement.getClass().getSimpleName() + ")");
                        }
                ));
        log.info("已注册 {} 个步骤处理器: {}", registry.size(), registry.keySet());
    }

    /**
     * 根据剧集状态获取对应的步骤处理器
     *
     * @param status 剧集状态
     * @return 对应的步骤处理器
     * @throws IllegalArgumentException 未找到匹配的处理器时抛出
     */
    public StepHandler get(EpisodeStatus status) {
        StepHandler handler = registry.get(status);
        if (handler == null) {
            throw new IllegalArgumentException("未找到步骤处理器: " + status);
        }
        return handler;
    }
}
