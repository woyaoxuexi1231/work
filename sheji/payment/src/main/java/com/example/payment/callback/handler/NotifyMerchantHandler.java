package com.example.payment.callback.handler;

import com.example.payment.callback.AbstractCallbackHandler;
import com.example.payment.callback.CallbackContext;
import org.springframework.stereotype.Component;

/**
 * 步骤6: 通知商户 —— 向商户系统发送支付成功通知。
 * 通知失败 → 终止链（商户不知情可能导致业务问题）。
 */
@Component("notifyMerchant")
public class NotifyMerchantHandler extends AbstractCallbackHandler {

    @Override
    protected void doHandle(CallbackContext ctx) {
        String merchantUrl = ctx.getParam("notifyUrl");
        if (merchantUrl == null || merchantUrl.isEmpty()) {
            skip("商户未配置通知地址，跳过通知");
        }

        // 模拟: 如果 notifyUrl 包含 "fail" 则模拟通知失败
        if (merchantUrl.contains("fail")) {
            fail("商户通知失败，目标地址不可达: " + merchantUrl);
        }

        ctx.set("merchantNotified", true);
    }
}
