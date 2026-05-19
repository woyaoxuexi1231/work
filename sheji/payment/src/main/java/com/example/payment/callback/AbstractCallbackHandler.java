package com.example.payment.callback;

/**
 * 抽象处理器 —— 提供 skip() / fail() 快捷方法。
 *
 * 子类在 doHandle 中：
 * - 正常 return → 链继续
 * - 调用 skip("原因") → 抛出 SkipException → 链跳过当前节点继续
 * - 调用 fail("原因") → 抛出 CallbackException → 链终止
 */
public abstract class AbstractCallbackHandler implements CallbackHandler {

    @Override
    public void handle(CallbackContext ctx) {
        doHandle(ctx);
    }

    /** 子类实现具体校验逻辑 */
    protected abstract void doHandle(CallbackContext ctx);

    /** 跳过当前步骤，链继续 */
    protected void skip(String reason) {
        throw new SkipException(reason);
    }

    /** 终止责任链 */
    protected void fail(String reason) {
        throw new CallbackException(reason);
    }
}
