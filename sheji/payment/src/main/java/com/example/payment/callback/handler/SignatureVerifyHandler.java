package com.example.payment.callback.handler;

import com.example.payment.callback.AbstractCallbackHandler;
import com.example.payment.callback.CallbackContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 步骤1: 验签 —— 验证回调请求的签名是否合法。
 * 签名不合法 → 终止链（可能伪造的回调）。
 */
@Component
@Order(1)
public class SignatureVerifyHandler extends AbstractCallbackHandler {

    @Override
    protected void doHandle(CallbackContext ctx) {
        String sign = ctx.getParam("sign");
        if (sign == null || sign.isEmpty()) {
            fail("签名缺失，疑似伪造回调");
        }
        if ("INVALID".equalsIgnoreCase(sign)) {
            fail("签名验证失败");
        }
        // 模拟验签通过
        ctx.set("signVerified", true);
    }
}
