package com.example.payment.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 责任链执行器 —— 核心。
 *
 * 链的拼接与顺序控制：通过 Spring 注入 List<CallbackHandler>，
 * 各 Handler 用 @Order(n) 控制顺序，Spring 自动排序后注入。
 *
 * 异常控制逻辑：
 * - SkipException    → 跳过当前节点，链继续（用于幂等跳过、可选步骤等场景）
 * - CallbackException → 终止链，返回失败
 * - 其他异常         → 终止链，返回失败
 *
 * 新增 / 删除 / 调整步骤：
 * - 新增：新建类 + @Component + @Order → 零修改已有代码
 * - 删除：去掉 @Component → 零修改已有代码
 * - 调序：改 @Order 值 → 零修改其他 Handler
 */
@Component
public class CallbackChainExecutor {

    private static final Logger log = LoggerFactory.getLogger(CallbackChainExecutor.class);

    private final List<CallbackHandler> handlers;

    /**
     * Spring 注入所有 CallbackHandler bean，并按 @Order 排序。
     */
    public CallbackChainExecutor(List<CallbackHandler> handlers) {
        this.handlers = handlers;
        log.info("CallbackChain 初始化完成，链顺序: {}",
                handlers.stream().map(CallbackHandler::name).collect(Collectors.toList()));
    }

    /**
     * 执行责任链。
     * 遍历每个 handler，根据异常类型决定继续 / 跳过 / 终止。
     */
    public CallbackResult execute(CallbackContext ctx) {
        long start = System.currentTimeMillis();

        for (CallbackHandler handler : handlers) {
            long stepStart = System.currentTimeMillis();

            try {
                handler.handle(ctx);
                // 正常执行 → 记录成功，继续下一个
                ctx.addStep(handler.name(), "PASSED", System.currentTimeMillis() - stepStart);
                log.debug("[{}] PASSED ({}ms)", handler.name(), System.currentTimeMillis() - stepStart);

            } catch (SkipException e) {
                // 跳过当前节点，链继续 → 这是设计意图，不是错误
                ctx.addStep(handler.name(), "SKIPPED: " + e.getMessage(),
                        System.currentTimeMillis() - stepStart);
                log.info("[{}] SKIPPED: {}", handler.name(), e.getMessage());

            } catch (CallbackException e) {
                // 业务失败 → 终止链
                ctx.addStep(handler.name(), "FAILED: " + e.getMessage(),
                        System.currentTimeMillis() - stepStart);
                log.error("[{}] FAILED: {}", handler.name(), e.getMessage());
                return CallbackResult.fail(handler.name(), e.getMessage(), ctx.getSteps());

            } catch (Exception e) {
                // 未知异常 → 终止链
                ctx.addStep(handler.name(), "ERROR: " + e.getMessage(),
                        System.currentTimeMillis() - stepStart);
                log.error("[{}] UNEXPECTED ERROR: {}", handler.name(), e.getMessage(), e);
                return CallbackResult.fail(handler.name(),
                        "未知异常: " + e.getMessage(), ctx.getSteps());
            }
        }

        return CallbackResult.success(ctx.getSteps(), System.currentTimeMillis() - start);
    }

    /** 获取当前链的 handler 名称列表（用于前端展示） */
    public List<String> getHandlerNames() {
        return handlers.stream().map(CallbackHandler::name).collect(Collectors.toList());
    }
}
