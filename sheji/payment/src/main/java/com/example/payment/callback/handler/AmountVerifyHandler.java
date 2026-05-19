package com.example.payment.callback.handler;

import com.example.payment.callback.AbstractCallbackHandler;
import com.example.payment.callback.CallbackContext;
import org.springframework.stereotype.Component;

/**
 * 步骤3: 金额校验 —— 校验回调中的金额与订单金额是否一致。
 * 金额不一致 → 终止链（可能是数据篡改）。
 */
@Component("amountVerify")
public class AmountVerifyHandler extends AbstractCallbackHandler {

    @Override
    protected void doHandle(CallbackContext ctx) {
        String callbackAmount = ctx.getParam("amount");
        if (callbackAmount == null) {
            fail("回调缺少金额参数");
        }
        double amount = Double.parseDouble(callbackAmount);
        if (amount <= 0) {
            fail("回调金额异常: " + amount);
        }
        // 模拟: 金额 <= 0.01 视为异常
        if (amount < 0.01) {
            fail("回调金额小于最小单位: " + amount);
        }
        ctx.set("verifiedAmount", amount);
    }
}
