package com.example.payment.callback.channel;

import com.example.payment.callback.CallbackChannelStrategy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 微信回调策略 —— MD5 验签 + XML 解析，6 步链（无风控上报）。
 */
@Component
public class WechatCallbackStrategy implements CallbackChannelStrategy {

    @Override
    public String getChannel() { return "WECHAT"; }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        // 模拟微信 MD5 验签：sign = MD5(params + key)
        String sign = params.get("sign");
        if (sign == null || sign.isEmpty()) return false;
        if ("INVALID".equalsIgnoreCase(sign)) return false;
        // 微信用 MD5
        return true;
    }

    @Override
    public Map<String, Object> parseCallback(Map<String, String> params) {
        // 模拟解析微信 XML 回调: <xml><return_code>...</return_code>...</xml>
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("returnCode", params.getOrDefault("return_code", "SUCCESS"));
        parsed.put("resultCode", params.getOrDefault("result_code", "SUCCESS"));
        parsed.put("totalFee", params.getOrDefault("amount", "0"));
        return parsed;
    }

    @Override
    public List<String> getHandlerNames() {
        // 微信不做风控上报（⑤ RiskReportHandler）
        return java.util.Arrays.asList(
                "signatureVerify",
                "idempotencyCheck",
                "amountVerify",
                "statusTransition",
                "notifyMerchant",
                "logPersistence"
        );
    }
}
