package com.example.payment.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 责任链执行器 —— 核心改造：每渠道独立链配置。
 *
 * 启动时遍历所有 CallbackChannelStrategy，按每渠道配置的 handler bean name
 * 列表，从 Spring 容器中查找对应 Handler bean，构建 Map<渠道, 链>。
 *
 * 新增 / 删除 / 调整渠道链步骤：
 * - 修改对应 CallbackChannelStrategy.getHandlerNames() 返回值即可
 * - 零修改 Handler 代码，零修改执行器代码
 */
@Component
public class CallbackChainExecutor {

    private static final Logger log = LoggerFactory.getLogger(CallbackChainExecutor.class);

    /** channel (upper case) → 该渠道的责任链 */
    private final Map<String, List<CallbackHandler>> chains;

    /** channel → handlerNames (for display) */
    private final Map<String, List<String>> chainNames;

    public CallbackChainExecutor(
            List<CallbackChannelStrategy> channelStrategies,
            ApplicationContext ctx) {

        Map<String, List<CallbackHandler>> chainsBuilder = new LinkedHashMap<>();
        Map<String, List<String>> namesBuilder = new LinkedHashMap<>();

        for (CallbackChannelStrategy strategy : channelStrategies) {
            String channel = strategy.getChannel().toUpperCase();
            List<String> handlerNames = strategy.getHandlerNames();

            List<CallbackHandler> handlerList = new ArrayList<>();
            for (String beanName : handlerNames) {
                CallbackHandler handler = ctx.getBean(beanName, CallbackHandler.class);
                handlerList.add(handler);
            }

            chainsBuilder.put(channel, Collections.unmodifiableList(handlerList));
            namesBuilder.put(channel, Collections.unmodifiableList(handlerNames));

            log.info("[{}] 责任链: {}", channel,
                    handlerList.stream().map(CallbackHandler::name).collect(Collectors.toList()));
        }

        this.chains = Collections.unmodifiableMap(chainsBuilder);
        this.chainNames = Collections.unmodifiableMap(namesBuilder);

        log.info("CallbackChainExecutor 初始化完成，共 {} 个渠道链: {}",
                chains.size(), chains.keySet());
    }

    /**
     * 执行指定渠道的责任链。
     */
    public CallbackResult execute(String channel, CallbackContext ctx) {
        List<CallbackHandler> handlers = chains.get(channel.toUpperCase());
        if (handlers == null) {
            throw new IllegalArgumentException("未找到渠道 " + channel + " 的责任链配置");
        }

        long start = System.currentTimeMillis();

        for (CallbackHandler handler : handlers) {
            long stepStart = System.currentTimeMillis();

            try {
                handler.handle(ctx);
                ctx.addStep(handler.name(), "PASSED", System.currentTimeMillis() - stepStart);
                log.debug("[{}][{}] PASSED ({}ms)", channel, handler.name(),
                        System.currentTimeMillis() - stepStart);

            } catch (SkipException e) {
                ctx.addStep(handler.name(), "SKIPPED: " + e.getMessage(),
                        System.currentTimeMillis() - stepStart);
                log.info("[{}][{}] SKIPPED: {}", channel, handler.name(), e.getMessage());

            } catch (CallbackException e) {
                ctx.addStep(handler.name(), "FAILED: " + e.getMessage(),
                        System.currentTimeMillis() - stepStart);
                log.error("[{}][{}] FAILED: {}", channel, handler.name(), e.getMessage());
                return CallbackResult.fail(handler.name(), e.getMessage(), ctx.getSteps());

            } catch (Exception e) {
                ctx.addStep(handler.name(), "ERROR: " + e.getMessage(),
                        System.currentTimeMillis() - stepStart);
                log.error("[{}][{}] UNEXPECTED ERROR: {}", channel, handler.name(), e.getMessage(), e);
                return CallbackResult.fail(handler.name(),
                        "未知异常: " + e.getMessage(), ctx.getSteps());
            }
        }

        return CallbackResult.success(ctx.getSteps(), System.currentTimeMillis() - start);
    }

    /** 获取指定渠道的 handler 名称列表 */
    public List<String> getHandlerNames(String channel) {
        List<String> names = chainNames.get(channel.toUpperCase());
        return names != null ? names : Collections.emptyList();
    }

    /** 获取指定渠道的 handler 显示名称列表 */
    public List<String> getHandlerDisplayNames(String channel) {
        List<CallbackHandler> handlers = chains.get(channel.toUpperCase());
        if (handlers == null) return Collections.emptyList();
        return handlers.stream().map(CallbackHandler::name).collect(Collectors.toList());
    }

    /** 获取所有已配置渠道 */
    public Set<String> channels() {
        return chains.keySet();
    }
}
