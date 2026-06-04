package com.example.payment.callback.channel;

import com.example.payment.callback.CallbackChannelStrategy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 银联回调策略 —— 证书验签，5 步链（无幂等校验 + 无风控上报）。
 */
@Component
public class UnionPayCallbackStrategy implements CallbackChannelStrategy {

    @Override
    public String getChannel() { return "UNIONPAY"; }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        // 模拟银联证书验签：需要验签名证书 + 公钥
        String sign = params.get("signature");
        if (sign == null || sign.isEmpty()) return false;
        if ("INVALID".equalsIgnoreCase(sign)) return false;
        // 银联用证书验签
        return true;
    }

    @Override
    public Map<String, Object> parseCallback(Map<String, String> params) {
        // 模拟解析银联回调: { respCode, respMsg, txnAmt, ... }
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("respCode", params.getOrDefault("respCode", "00"));
        parsed.put("respMsg", params.getOrDefault("respMsg", "成功"));
        parsed.put("txnAmt", params.getOrDefault("amount", "0"));
        return parsed;
    }

    @Override
    public List<String> getHandlerNames() {
        // 银联不做幂等（②）+ 不做风控（⑤）
        return java.util.Arrays.asList(
                "signatureVerify",
                "amountVerify",
                "statusTransition",
                "notifyMerchant",
                "logPersistence"
        );
    }
}
