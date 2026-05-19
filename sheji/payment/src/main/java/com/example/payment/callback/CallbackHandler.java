package com.example.payment.callback;

/**
 * 回调处理器接口 —— 责任链节点。
 *
 * 约定：
 * - 正常执行完 → 链继续到下一个节点
 * - 抛出 SkipException → 跳过当前节点，链继续（如：幂等校验发现已处理）
 * - 抛出其他异常 → 链终止，返回失败
 */
@FunctionalInterface
public interface CallbackHandler {
    void handle(CallbackContext ctx);

    default String name() {
        return getClass().getSimpleName();
    }
}
