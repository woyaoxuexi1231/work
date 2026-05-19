package com.example.payment.callback.handler;

import com.example.payment.callback.AbstractCallbackHandler;
import com.example.payment.callback.CallbackContext;
import org.springframework.stereotype.Component;

/**
 * 步骤7: 日志持久化 —— 记录回调处理日志（审计需要）。
 * 这是链的最后一个节点，不应该失败。
 */
@Component("logPersistence")
public class LogPersistenceHandler extends AbstractCallbackHandler {

    @Override
    protected void doHandle(CallbackContext ctx) {
        // 模拟持久化回调日志
        ctx.set("logId", "LOG-" + System.currentTimeMillis());
        // 此步骤设计为永不失败
    }
}
