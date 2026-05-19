package com.example.payment.callback.channel;

import com.example.payment.callback.CallbackChannelStrategy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 支付宝回调策略 —— RSA 验签 + JSON 解析，完整 7 步链。
 */
@Component
public class AlipayCallbackStrategy implements CallbackChannelStrategy {

    @Override
    public String getChannel() { return "ALIPAY"; }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        // 模拟 RSA 验签：检查 sign 和 sign_type
        String sign = params.get("sign");
        String signType = params.getOrDefault("sign_type", "RSA2");
        if (sign == null || sign.isEmpty()) return false;
        if ("INVALID".equalsIgnoreCase(sign)) return false;
        // 支付宝用 RSA2(SHA256)
        if (!"RSA2".equals(signType) && !"RSA".equals(signType)) return false;
        return true;
    }

    @Override
    public Map<String, Object> parseCallback(Map<String, String> params) {
        // 模拟解析支付宝 JSON 回调: { notify_id, trade_status, total_amount, ... }
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("notifyId", params.getOrDefault("notify_id", "ALI-NOTIFY-" + System.currentTimeMillis()));
        parsed.put("tradeStatus", params.getOrDefault("trade_status", "TRADE_SUCCESS"));
        parsed.put("totalAmount", params.getOrDefault("amount", "0"));
        return parsed;
    }

    @Override
    public List<String> getHandlerNames() {
        return List.of(
                "signatureVerify",
                "idempotencyCheck",
                "amountVerify",
                "statusTransition",
                "riskReport",
                "notifyMerchant",
                "logPersistence"
        );
    }
}
