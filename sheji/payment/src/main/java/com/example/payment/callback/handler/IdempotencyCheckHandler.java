package com.example.payment.callback.handler;

import com.example.payment.callback.AbstractCallbackHandler;
import com.example.payment.callback.CallbackContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 步骤2: 幂等性校验 —— 检查此通知是否已处理过。
 * 已处理 → 抛出 SkipException 跳过当前步骤（链继续，这是正常场景）。
 * 未处理 → 标记为已处理，继续。
 */
@Component
@Order(2)
public class IdempotencyCheckHandler extends AbstractCallbackHandler {

    @Override
    protected void doHandle(CallbackContext ctx) {
        String idempotentKey = ctx.getChannel() + ":" + ctx.getOrderNo();
        if (ctx.isProcessed(idempotentKey)) {
            // 已处理过 → 跳过，但不阻止链（之后的通知商户等步骤仍需要走）
            skip("订单 " + ctx.getOrderNo() + " 已处理过，幂等跳过");
        }
        ctx.markProcessed(idempotentKey);
        ctx.set("idempotentKey", idempotentKey);
    }
}
