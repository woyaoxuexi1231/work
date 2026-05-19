package com.example.payment.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 策略上下文 —— 核心：利用 Spring 自动注入 List<PaymentStrategy>，
 * 启动时构建 Map，实现 O(1) 路由 + 零修改接入新渠道。
 *
 * 新渠道接入只需两步：
 * 1. 新建类 implements PaymentStrategy
 * 2. 标注 @Component
 * → Spring 自动注入到 List，Registry 自动收录，无需改任何已有代码。
 */
@Component
public class PaymentStrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(PaymentStrategyRegistry.class);

    private final Map<String, PaymentStrategy> registry;

    /**
     * Spring 会把容器中所有 PaymentStrategy 的实现类自动注入到这个 List。
     * Registry 在构造阶段将 List 转为 Map，key = channel 标识（如 "ALIPAY"）。
     */
    public PaymentStrategyRegistry(List<PaymentStrategy> strategies) {
        this.registry = strategies.stream()
                .collect(Collectors.toMap(
                        s -> s.getChannel().toUpperCase(),
                        Function.identity(),
                        (existing, replacement) -> existing  // 渠道名冲突时保留已有
                ));
        log.info("PaymentStrategyRegistry 初始化完成，已注册渠道: {}", registry.keySet());
    }

    /** O(1) 获取策略，无 if-else，无 List 遍历匹配 */
    public PaymentStrategy get(String channel) {
        PaymentStrategy strategy = registry.get(channel.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的支付渠道: " + channel
                    + "，当前已注册: " + registry.keySet());
        }
        return strategy;
    }

    /** 获取所有已注册渠道 */
    public Set<String> channels() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /** 获取所有策略对象（用于前端展示渠道列表） */
    public Collection<PaymentStrategy> all() {
        return registry.values();
    }
}
