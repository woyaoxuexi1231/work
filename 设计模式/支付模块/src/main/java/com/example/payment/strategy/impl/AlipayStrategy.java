package com.example.payment.strategy.impl;

import com.example.payment.model.PaymentRequest;
import com.example.payment.strategy.PaymentStrategy;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class AlipayStrategy implements PaymentStrategy {

    @Override
    public String getChannel() { return "ALIPAY"; }

    @Override
    public String getDisplayName() { return "支付宝"; }

    @Override
    public Map<String, Object> pay(PaymentRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("channel", "ALIPAY");
        result.put("orderNo", request.getOrderNo());
        result.put("amount", request.getAmount());
        result.put("tradeNo", "ALI" + System.currentTimeMillis());
        result.put("qrCode", "https://qr.alipay.com/" + request.getOrderNo());
        return result;
    }

    @Override
    public Map<String, Object> query(String orderNo) {
        Map<String, Object> result = new HashMap<>();
        result.put("channel", "ALIPAY");
        result.put("orderNo", orderNo);
        result.put("status", "SUCCESS");
        result.put("amount", new BigDecimal("99.00"));
        return result;
    }
}
