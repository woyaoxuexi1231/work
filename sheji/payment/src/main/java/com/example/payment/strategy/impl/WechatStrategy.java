package com.example.payment.strategy.impl;

import com.example.payment.model.PaymentRequest;
import com.example.payment.strategy.PaymentStrategy;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class WechatStrategy implements PaymentStrategy {

    @Override
    public String getChannel() { return "WECHAT"; }

    @Override
    public String getDisplayName() { return "微信支付"; }

    @Override
    public Map<String, Object> pay(PaymentRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("channel", "WECHAT");
        result.put("orderNo", request.getOrderNo());
        result.put("amount", request.getAmount());
        result.put("prepayId", "wx" + System.currentTimeMillis());
        result.put("h5Url", "https://pay.weixin.qq.com/" + request.getOrderNo());
        return result;
    }

    @Override
    public Map<String, Object> query(String orderNo) {
        Map<String, Object> result = new HashMap<>();
        result.put("channel", "WECHAT");
        result.put("orderNo", orderNo);
        result.put("status", "SUCCESS");
        result.put("amount", new BigDecimal("88.50"));
        return result;
    }
}
