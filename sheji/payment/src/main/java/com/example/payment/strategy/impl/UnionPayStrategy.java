package com.example.payment.strategy.impl;

import com.example.payment.model.PaymentRequest;
import com.example.payment.strategy.PaymentStrategy;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class UnionPayStrategy implements PaymentStrategy {

    @Override
    public String getChannel() { return "UNIONPAY"; }

    @Override
    public String getDisplayName() { return "银联支付"; }

    @Override
    public Map<String, Object> pay(PaymentRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("channel", "UNIONPAY");
        result.put("orderNo", request.getOrderNo());
        result.put("amount", request.getAmount());
        result.put("tn", "UP" + System.currentTimeMillis());
        return result;
    }

    @Override
    public Map<String, Object> query(String orderNo) {
        Map<String, Object> result = new HashMap<>();
        result.put("channel", "UNIONPAY");
        result.put("orderNo", orderNo);
        result.put("status", "SUCCESS");
        result.put("amount", new BigDecimal("156.00"));
        return result;
    }
}
