package com.example.payment.callback.handler;

import com.example.payment.callback.AbstractCallbackHandler;
import com.example.payment.callback.CallbackChannelRegistry;
import com.example.payment.callback.CallbackChannelStrategy;
import com.example.payment.callback.CallbackContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 步骤1: 验签 —— 委托给渠道策略做差异化验签。
 * 签名不合法 → 终止链（可能伪造的回调）。
 */
@Component("signatureVerify")
public class SignatureVerifyHandler extends AbstractCallbackHandler {

    private final CallbackChannelRegistry channelRegistry;

    public SignatureVerifyHandler(CallbackChannelRegistry channelRegistry) {
        this.channelRegistry = channelRegistry;
    }

    @Override
    protected void doHandle(CallbackContext ctx) {
        CallbackChannelStrategy strategy = channelRegistry.get(ctx.getChannel());
        Map<String, Object> parsed = strategy.parseCallback(ctx.getRawParams());
        ctx.set("parsedCallback", parsed);

        if (!strategy.verifySignature(ctx.getRawParams())) {
            fail("签名验证失败（" + ctx.getChannel() + "）");
        }
        ctx.set("signVerified", true);
    }
}
