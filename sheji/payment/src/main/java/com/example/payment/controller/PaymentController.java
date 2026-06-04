package com.example.payment.controller;

import com.example.payment.model.ApiResult;
import com.example.payment.model.PaymentRequest;
import com.example.payment.strategy.PaymentStrategy;
import com.example.payment.strategy.PaymentStrategyRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentStrategyRegistry registry;

    public PaymentController(PaymentStrategyRegistry registry) {
        this.registry = registry;
    }

    /** 获取所有已注册渠道 */
    @GetMapping("/channels")
    public ApiResult channels() {
        List<Map<String, String>> list = registry.all().stream().map(s -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("channel", s.getChannel());
            m.put("displayName", s.getDisplayName());
            return m;
        }).collect(Collectors.toList());
        return ApiResult.ok("已注册 " + list.size() + " 个渠道", java.util.Collections.singletonMap("channels", list));
    }

    /** 发起支付 —— 根据 channel 自动路由到对应策略 */
    @PostMapping("/pay")
    public ApiResult pay(@RequestBody PaymentRequest request) {
        PaymentStrategy strategy = registry.get(request.getChannel());
        Map<String, Object> result = strategy.pay(request);
        return ApiResult.ok(result);
    }

    /** 查询订单 */
    @GetMapping("/query")
    public ApiResult query(@RequestParam String channel, @RequestParam String orderNo) {
        PaymentStrategy strategy = registry.get(channel);
        Map<String, Object> result = strategy.query(orderNo);
        return ApiResult.ok(result);
    }
}
