package com.example.payment.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 回调渠道策略注册表 —— 同 PaymentStrategyRegistry 的设计思路。
 * Spring 自动注入所有 CallbackChannelStrategy 实现，构建 Map 做 O(1) 路由。
 */
@Component
public class CallbackChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallbackChannelRegistry.class);

    private final Map<String, CallbackChannelStrategy> registry;

    public CallbackChannelRegistry(List<CallbackChannelStrategy> strategies) {
        this.registry = strategies.stream()
                .collect(Collectors.toMap(
                        s -> s.getChannel().toUpperCase(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
        log.info("CallbackChannelRegistry 初始化完成，已注册渠道: {}", registry.keySet());
    }

    public CallbackChannelStrategy get(String channel) {
        CallbackChannelStrategy strategy = registry.get(channel.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的回调渠道: " + channel
                    + "，当前已注册: " + registry.keySet());
        }
        return strategy;
    }

    public Set<String> channels() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    public Collection<CallbackChannelStrategy> all() {
        return registry.values();
    }
}
